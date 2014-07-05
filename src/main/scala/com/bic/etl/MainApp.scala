package com.bic.etl

import java.io.{OutputStreamWriter, File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}
import java.util.Properties

import org.apache.spark.SparkContext._
import org.apache.spark.{Logging, SparkConf, SparkContext}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.JsonAST.{JObject,JString}
import org.json4s.jackson.JsonMethods.parse

import scala.annotation.tailrec
import scala.io.Source

/**
 * Created by patrick.jiang on 7/4/14.
 *
 */
object MainApp extends App with Logging {

  val commonProperties = new Properties()
  val cpFile = Source.fromFile("/mnt/backup/bic/conf/cccdr.properties").reader()
  commonProperties.load(cpFile)
  cpFile.close()

  val workMode = commonProperties.getProperty("spark.work.mode")
  logInfo(s"workMode=$workMode")
  val appName = commonProperties.getProperty("spark.app.name")
  logInfo(s"appName=$appName")
  val dimDir = commonProperties.getProperty("data.dim.dir")
  logInfo(s"dimDir=$dimDir")
  val inDir = commonProperties.getProperty("data.in.dir")
  logInfo(s"inDir=$inDir")
  val outDir = commonProperties.getProperty("data.out.dir")
  logInfo(s"outDir=$outDir")
  val confDir = commonProperties.getProperty("data.conf.dir")
  logInfo(s"confDir=$confDir")
  val rawDir = commonProperties.getProperty("data.raw.dir")
  logInfo(s"rawDir=$rawDir")
  val cdrPattern = commonProperties.getProperty("cdrdata.fn.regexp")
  logInfo(s"cdrPattern=$cdrPattern")
  val propPattern = commonProperties.getProperty("cdrfmt.fn.regexp")
  logInfo(s"propPattern=$propPattern")
  val pagePattern = commonProperties.getProperty("page.regexp")
  logInfo(s"pagePattern=$pagePattern")

  val sparkConf = new SparkConf()
    .setMaster(workMode)
    .setAppName(appName)
  val sc = new SparkContext(sparkConf)

  val fixedF = List("from", "op", "msisdn", "imsi", "imei",
    "subscription", "language", "session_time", "service_code",
    "bearer", "page_sent", "duration",
    "vlr_number", "session_end", "action_url", "extra")

  val tws = DateTime.now().getMillis

  step(rawDir, step1)
  step(confDir, step2)
  step(inDir, step3)

  def step(root: String, fun: (String, String) => Unit) = {
    Paths.get(root).toFile.listFiles().filter(_.isDirectory)
      .map(f => f.getName -> f.listFiles().filter(_.isDirectory).map(_.getAbsolutePath))
      .map(kv => kv._2.foreach(v => fun(kv._1, v)))
  }

  def step1(from: String, sources: String) = {
    Paths.get(sources).toFile.listFiles().foreach(f => {
      f.getName.endsWith(".properties") match {
        case true =>
          val targetDir = Paths.get(s"${f.getParent.replaceAll(rawDir, confDir)}/$tws")
          val target = Paths.get(s"${f.getParent.replaceAll(rawDir, confDir)}/$tws/${f.getName}")
          targetDir.toFile.mkdirs()
          Files.move(f.toPath, target)
        case _ =>
          val targetDir = Paths.get(s"${f.getParent.replaceAll(rawDir, inDir)}/$tws")
          val target = Paths.get(s"${f.getParent.replaceAll(rawDir, inDir)}/$tws/${f.getName}")
          targetDir.toFile.mkdirs()
          Files.move(f.toPath, target)
      }
    })
  }

  def step2(from: String, sources: String) = {
    //    logError(s"$sources/$tws")
    new File(s"$sources/$tws").listFiles()
      .filter(_.getName.endsWith(".properties")).foreach(f => {
      val pattern = propPattern.r
      val pattern(op) = f.getName
      val jfn = "fields"
      val prop = new Properties()
      val inp = new FileInputStream(f)
      prop.load(inp)
      inp.close()
      val ast = parse(prop.getProperty(jfn))
      prop.setProperty(s"from.index", "0")
      prop.setProperty(s"from.type", "String")
      prop.setProperty(s"from.format", "")
      prop.setProperty(s"op.index", "1")
      prop.setProperty(s"op.type", "String")
      prop.setProperty(s"op.format", "")
      var index = 2
      ast transform {
        case JObject(List((k1, JString("action_url")), (k2, v2), (k3, v3))) =>
          prop.setProperty(s"action_url.index", index.toString)
          prop.setProperty(s"action_url.$k2", v2.values.toString)
          prop.setProperty(s"action_url.$k3", "(file:[^\\,\\r\\n?]+|https?:[^\\,\\r\\n?]+)")
          index += 1
          JObject()
        case JObject(List((k1, v1), (k2, v2), (k3, v3))) =>
          prop.setProperty(s"${v1.values}.index", index.toString)
          prop.setProperty(s"${v1.values}.$k2", v2.values.toString)
          prop.setProperty(s"${v1.values}.$k3", v3.values.toString)
          index += 1
          JObject()
      }
      prop.remove(jfn)
      val now = new DateTime()
      val outputName = f.getAbsolutePath.replaceAll(f.getName, s"$from-$op-${now.getMillis}.conf")
      val output = new FileOutputStream(outputName)
      prop.store(output, s"new created data format for etl at $now")
      output.flush()
      output.close()
    })
  }

  def step3(from: String, inPath: String) = {
    val confPath = inPath.replaceAll(inDir, confDir)
    implicit val tps: Map[String, List[CDRFormatter]] = Paths.get(s"$confPath/$tws")
      .toFile.listFiles().filter(_.getName.endsWith(".conf"))
      .map(f => {
      val pattern = ".*-(.*)-\\d{13}.conf$*".r
      val pattern(op) = f.getName
      val prop = new Properties()
      prop.load(new FileInputStream(f))
      (op, new CDRFormatter(prop))
    }).groupBy(p => p._1).map(p => (p._1, p._2.map(x => x._2).toList))

    logError(s"$tps")

    //    logError(s"$inPath/$tws")
    if (Paths.get(s"$inPath/$tws").toFile.listFiles().length > 0) {
      val rawData = sc.wholeTextFiles(s"$inPath/$tws")
        .map(kv => kv._2.split("\n").map(v => {
        val pattern = cdrPattern.r
        val pattern(op) = kv._1
        s"$from;$op;$v"
      }))
        .flatMap(p => p).map(convertToStd)

      val dimUser = sc.textFile(s"$dimDir/$from/user").map(p => {
        val temp = p.split(";")
        (temp.head.toInt, temp.tail.mkString(";"))
      })
      val uc = dimUser.sortByKey(ascending = true, 1).collect()
      var latestUserId = if (uc.size == 0) 0 else uc.last._1.toInt
      val newFoundUsers = rawData.map(updateUserDim)
        .leftOuterJoin(dimUser.map(p => (p._2, p._1)))
        .filter(p => p._2._2.isEmpty).distinct().collect()
      val newUserFile = new File(s"$dimDir/$from/user/${DateTime.now().getMillis}")
      val userWriter = new OutputStreamWriter(new FileOutputStream(newUserFile))
      val currentUsers: Array[(String, Any)] = newFoundUsers map {
        case (k, (_, None)) =>
          latestUserId += 1
          val line = s"$latestUserId;$k\n"
          userWriter.write(line)
          (k.replaceAll("\\+", "\\\\+"), latestUserId)
        case (k, (_, id)) => (k.replaceAll("\\+", "\\\\+"), id)
      }
      userWriter.flush()
      userWriter.close()
      val dimPage = sc.textFile(s"$dimDir/$from/page").map(p => {
        val temp = p.split(";")
        (temp.head, temp.last)
      })
      val pc = dimPage.sortByKey(ascending = true, 1).collect()
      var latestPageId = if (pc.size == 0) 0 else pc.last._1.toInt
      val newFoundPages = rawData.map(updatePageDim).flatMap(p => p).map(p => (p, 1))
        .leftOuterJoin(dimPage).filter(p => p._2._2.isEmpty).filter(p=>p._1.length>0).distinct().collect()
      val newPageFile = new File(s"$dimDir/$from/page/${DateTime.now().getMillis}")
      val pageWriter = new OutputStreamWriter(new FileOutputStream(newPageFile))
      val currentPages: Array[(String, Any)] = newFoundPages map {
        case (k, (v, None)) =>
          latestPageId += 1
          val line = s"$latestPageId;$k\n"
          pageWriter.write(line)
          (k, latestPageId)
        case (k, (_, id)) => (k, id)
      }
      pageWriter.flush()
      pageWriter.close()

      val dims = List(currentPages.toList, currentUsers.toList)
      logError(s"$dims")
      val replRaw = rawData.map(p => repl(p, dims)).cache()

      replRaw.saveAsTextFile("/tmp/11")
      //      replRaw.map(genKV).reduceByKey(_ + _, 1).saveAsTextFile("/tmp/12")
      //      rawData.map(replacePage).flatMap(p => p).map(p => (p, 1)).reduceByKey(_ + _, 1).saveAsTextFile("/tmp/13")
    }
  }

  def chooseFormatter(text: String)(implicit tps: Map[String, List[CDRFormatter]]) = {
    val op = text.split(";")(1)
    val propsOfOp = tps.get(op).get
    //    logError(s"$text")
    propsOfOp.find(prop => {
      //      logError(s"1=${prop.getSessionTime(text, "session_time")}")
      //      logError(s"2=${prop.getTs}")
      prop.getSessionTime(text, "session_time") match {
        case Some(t) =>
          if (t.asInstanceOf[Long] * 1000L >= prop.getTs) {
            true
          } else {
            false
          }
        case None => false
      }
    }) match {
      case Some(taskConfig) => taskConfig
      case None => throw new Exception
    }
  }


  def convertToStd(text: String)(implicit tps: Map[String, List[CDRFormatter]]) = {
        logError(s"cts=$text")
    val formatter = chooseFormatter(text)
    //    logError(s"$delimiter")
    //    logError(s"$fixedF")
    fixedF.map(fn => formatter.getFieldValue(text, fn).getOrElse("")).mkString(";")
  }

  @tailrec
  def repl(text: String, dims: List[List[(String, Any)]]): String = {
    if (dims.tail.length == 0) dims.head.foldLeft(text)((acc, e) => acc.replaceAll(e._1, e._2.toString))
    else repl(dims.head.foldLeft(text)((acc, e) => acc.replaceAll(e._1, e._2.toString)), dims.tail)
  }

  def updateUserDim(text: String) = {
    //    logError(s"uu=$text")
    val delimiter = ";"
    val from = text.split(delimiter)(fixedF.indexOf("from"))
    val op = text.split(delimiter)(fixedF.indexOf("op"))
    val msisdn = text.split(delimiter)(fixedF.indexOf("msisdn"))
    val imsi = text.split(delimiter)(fixedF.indexOf("imsi"))
    val imei = text.split(delimiter)(fixedF.indexOf("imei"))
    val key = List(from, op, msisdn, imsi, imei).mkString(delimiter)
    val value = 1
    (key, value)
  }

  def updatePageDim(text: String) = {
    //    logError(s"up=$text")
    val delimiter = ";"
    text.split(delimiter)(fixedF.indexOf("action_url")).split(",").toList
//    pagePattern.r.findAllMatchIn(pages).map(p => s"$p").toList
  }
}


class CDRFormatter(properties: Properties) extends Serializable with Logging {
  val tsPattern = "yyyy-MM-dd HH:mm:SS"
  val tsName = "format_start_date_time"

  def getProperties = properties

  def delimiter = properties.getProperty("delimiter", ";")

  def getTs = DateTime.parse(properties.getProperty(tsName), DateTimeFormat.forPattern(tsPattern)).getMillis

  def getSessionTime(text: String, key: String, aggPattern: Option[String] = None) = {
    try {
      val index = properties.getProperty(s"$key.index").toInt
      //      logError(s"index=$index")
      val delimiter = properties.getProperty(s"delimiter")
      val tp = properties.getProperty(s"$key.type").toUpperCase
      //      logError(s"type=$tp")
      val fmt = properties.getProperty(s"$key.format")
      //      logError(s"fmt=$fmt")
      val ovs = text.split(delimiter)(index)
      //      logError(s"ovs=$ovs")
      val temp = tp match {
        case "LONG" => new DateTime(ovs.toLong * 1000L)
        case "INT" => new DateTime(ovs.toLong * 1000L)
        case _ => DateTime.parse(ovs, DateTimeFormat.forPattern(fmt))
      }
      aggPattern match {
        case None =>
          Some(temp.getMillis)
        case Some(p) =>
          Some(temp.toString(p))
      }
    } catch {
      case e: Exception => None
    }
  }

  def getFieldValue(text: String, key: String) = {
    try {
      val delimiter = properties.getProperty(s"delimiter")
      val index = properties.getProperty(s"$key.index").toInt
      val tp = properties.getProperty(s"$key.type").toUpperCase
      val fmt = properties.getProperty(s"$key.format")
      val ovs = text.split(delimiter)(index)
      Some(tp match {
        case "INT" => ovs.toInt
        case "LONG" => ovs.toLong
        case _ =>
          key.toLowerCase match {
            case "action_url"=>fmt.r.findAllMatchIn(ovs).map(p => s"$p").mkString(",")
            case _ => ovs
          }
      })
    } catch {
      case e: Exception => None
    }
  }

}


object TempTool extends App {

  //  val prop = new Properties()
  //  prop.load(new FileInputStream(new File("/home/patrick.jiang/Desktop/temp1/xue/cc_cdr_raw_data/test/raw.properties")))
  //  val jss = prop.getProperty("fields")
  //  prop.remove("fields")
  //
  //  val ast = parse(jss)
  //
  //  var index :Int = 0
  //  ast transform {
  //    case JObject(List((k1,v1), (k2,v2), (k3,v3))) =>
  //      println(s"($k1,$v1)($k2,$v2)($k3,$v3)")
  //      prop.setProperty(s"${v1.values}.index",index.toString)
  //      prop.setProperty(s"${v1.values}.$k2",v2.values.toString)
  //      prop.setProperty(s"${v1.values}.$k3",v3.values.toString)
  //      index += 1
  //      JObject()
  //  }
  //
  //  val output = new FileOutputStream("/tmp/config.properties")
  //  prop.store(output, "this is new")
  //  output.flush()
  //  output.close()

  //  val p1 = "/home/patrick.jiang/Desktop/temp1/xue/cc_cdr_raw_data/var/lib/bic/ds/historical/cc_cdr"
  //  val p2 = "/mnt/backup/bic/raw/idc/cccdr/"
  //  Paths.get(p1).toFile.listFiles().filter(_.isDirectory).foreach(d => {
  //    d.listFiles().foreach(f => {
  //      Files.copy(f.toPath, Paths.get(s"$p2${f.getName}"), StandardCopyOption.REPLACE_EXISTING)
  //    })
  //  })

  //  val t = "data_format-submscdr-5-vodafone_india-20120921.properties"
  //  val pattern = "data_format-.*-(.*)-.*.properties".r
  //  val pattern(op) = t
  //  println(op)
  //  val pages = "#702*#=file:/mcel/xumii/twitter/en/twitter_login.xml#id_entry_input_pincode,1234=file:/mcel/xumii/twitter/en/twitter_login.xml#main_menu,2=file:/mcel/xumii/twitter/en/twitter_post_tweet.xml,1=file:/mcel/xumii/twitter/en/twitter_post_tweet.xml#id87,hello everyone2012-08-16T08:02:38=file:/mcel/xumii/twitter/en/twitter_post_tweet.xml#id90,9=file:/mcel/xumii/twitter/en/twitter_login.xml#main_menu,1=file:/mcel/xumii/twitter/en/twitter_timeline.xml#timeline_brief,1=file:/mcel/xumii/twitter/en/twitter_timeline.xml#id12"
  //  val p = "(file:[^\\,\r\n?]+|https?:[^\\,\r\n?]+)"
  //  val xx = p.r.findAllMatchIn(pages).toList
  //  println(xx)
  //  @tailrec
  //  def repl(text:String, dims:List[List[(String,Any)]]): String= {
  //    if (dims.tail.length == 0) {
  //      println(dims.head)
  //      println(dims.tail)
  //      println(text)
  //      dims.head.foldLeft(text)((acc, e) => {
  //        println(s"acc=$acc")
  //        println(s"${e._1}->${e._2}")
  //        acc.replaceAll(e._1, e._2.toString)
  //      })
  //    }
  //    else repl(dims.head.foldLeft(text)((acc, e) => acc.replaceAll(e._1, e._2.toString)),dims.tail)
  //  }
  //
  //  val src = "idc;bndosat;+331100800107;999100100800107;;;en;1344939907;#902;U2;2;121;33999999999;;#902*#=37,1=1;"
  //  val ds = List(List(("file:/mcel/xumii/subms/en/subms.xml#id6",1), ("file:/mcel/xumii/subms/en/subms.xml#id6",2), ("file:/mcel/xumii/subms/en/subms.xml#id6",3), ("file:/mcel/xumii/subms/en/subms.xml#id6",4), ("file:/mcel/xumii/subms/en/subms.xml#id6",5), ("file:/mcel/xumii/subms/en/subms.xml#id6",6), ("file:/mcel/xumii/twitter/en/twitter_login.xml#id_registration_acc_orig",7), ("file:/mcel/xumii/twitter/en/twitter_login.xml#id_registration_acc_orig",8), ("file:/mcel/xumii/twitter/en/twitter_login.xml#id_registration_acc_orig",9), ("file:/mcel/xumii/twitter/en/twitter_login.xml#id_registration_acc_orig",10), ("file:/mcel/xumii/twitter/en/twitter_login.xml#id_registration_acc_orig",11), ("file:/mcel/xumii/twitter/en/twitter_login.xml#id_registration_acc_orig",12), ("file:/mcel/xumii/facebook/en/facebook_login.xml#main_menu",13), ("file:/mcel/xumii/facebook/en/facebook_login.xml#main_menu",14), ("file:/mcel/xumii/facebook/en/facebook_login.xml#main_menu",15), ("file:/mcel/xumii/twitter/en/twitter_post_tweet.xml#id90",16), ("file:/mcel/xumii/twitter/en/twitter_post_tweet.xml#id90",17), ("file:/mcel/xumii/twitter/en/twitter_post_tweet.xml#id90",18), ("file:/mcel/xumii/facebook/en/facebook_login.xml#init_facebook_account_main",19), ("file:/mcel/xumii/facebook/en/facebook_login.xml#init_facebook_account_main",20), ("file:/mcel/xumii/facebook/en/facebook_login.xml#init_facebook_account_main",21), ("file:/mcel/xumii/twitter/en/twitter_login.xml#main_menu",22), ("file:/mcel/xumii/twitter/en/twitter_login.xml#main_menu",23), ("file:/mcel/xumii/twitter/en/twitter_login.xml#main_menu",24), ("file:/mcel/xumii/twitter/en/twitter_login.xml#main_menu",25), ("file:/mcel/xumii/twitter/en/twitter_login.xml#main_menu",26), ("file:/mcel/xumii/twitter/en/twitter_login.xml#main_menu",27), ("file:/mcel/xumii/twitter/en/twitter_post_tweet.xml#id87",28), ("file:/mcel/xumii/twitter/en/twitter_post_tweet.xml#id87",29), ("file:/mcel/xumii/twitter/en/twitter_post_tweet.xml#id87",30), ("file:/mcel/xumii/facebook/en/facebook_login.xml#login_page",31), ("file:/mcel/xumii/facebook/en/facebook_login.xml#login_page",32), ("file:/mcel/xumii/facebook/en/facebook_login.xml#login_page",33), ("file:/mcel/xumii/facebook/en/facebook_status.xml",34), ("file:/mcel/xumii/facebook/en/facebook_status.xml",35), ("file:/mcel/xumii/facebook/en/facebook_status.xml",36), ("file:/mcel/xumii/subms/en/subms.xml#id18",37), ("file:/mcel/xumii/subms/en/subms.xml#id18",38), ("file:/mcel/xumii/subms/en/subms.xml#id18",39), ("file:/mcel/xumii/subms/en/subms.xml#id18",40), ("file:/mcel/xumii/subms/en/subms.xml#id18",41), ("file:/mcel/xumii/subms/en/subms.xml#id18",42), ("file:/mcel/xumii/twitter/en/twitter_timeline.xml#id12",43), ("file:/mcel/xumii/twitter/en/twitter_timeline.xml#id12",44), ("file:/mcel/xumii/twitter/en/twitter_timeline.xml#id12",45), ("file:/mcel/xumii/twitter/en/twitter_login.xml#id_entry_input_pincode",46), ("file:/mcel/xumii/twitter/en/twitter_login.xml#id_entry_input_pincode",47), ("file:/mcel/xumii/twitter/en/twitter_login.xml#id_entry_input_pincode",48), ("file:/mcel/xumii/facebook/en/facebook_login.xml#id49",49), ("file:/mcel/xumii/facebook/en/facebook_login.xml#id49",50), ("file:/mcel/xumii/facebook/en/facebook_login.xml#id49",51), ("file:/mcel/xumii/twitter/en/twitter_timeline.xml#timeline_brief",52), ("file:/mcel/xumii/twitter/en/twitter_timeline.xml#timeline_brief",53), ("file:/mcel/xumii/twitter/en/twitter_timeline.xml#timeline_brief",54), ("file:/mcel/xumii/twitter/en/twitter_post_tweet.xml",55), ("file:/mcel/xumii/twitter/en/twitter_post_tweet.xml",56), ("file:/mcel/xumii/twitter/en/twitter_post_tweet.xml",57)), List(("idc;andosat;+33608995817;;",1), ("idc;andosat;+33608995817;;",2), ("idc;indosat;+331100800004;999100100800004;",3), ("idc;indosat;+331100800004;999100100800004;",4), ("idc;indosat;+331100800003;999100100800003;",5), ("idc;indosat;+33608995817;;",6), ("idc;indosat;+331100800002;999100100800002;",7), ("idc;indosat;+331100800101;999100100800101;",8), ("idc;indosat;+331100800101;999100100800101;",9), ("idc;indosat;+331100800107;999100100800107;",10), ("idc;andosat;+331100800002;999100100800002;",11), ("idc;andosat;+331100800002;999100100800002;",12), ("idc;andosat;+331100800004;999100100800004;",13), ("idc;andosat;+331100800004;999100100800004;",14), ("idc;andosat;+331100800004;999100100800004;",15), ("idc;andosat;+331100800004;999100100800004;",16), ("idc;andosat;+33608995865;;",17), ("idc;andosat;+33608995865;;",18), ("idc;indosat;+331100300003;;",19), ("idc;andosat;+331100300003;;",20), ("idc;andosat;+331100300003;;",21), ("idc;indosat;+33608995865;;",22), ("idc;bndosat;+331100800003;999100100800003;",23), ("idc;bndosat;+331100800003;999100100800003;",24), ("idc;bndosat;+331100800101;999100100800101;",25), ("idc;bndosat;+331100800101;999100100800101;",26), ("idc;bndosat;+331100800101;999100100800101;",27), ("idc;bndosat;+331100800101;999100100800101;",28), ("idc;bndosat;+331100800107;999100100800107;",29), ("idc;bndosat;+331100800107;999100100800107;",30)))
  //  val r = repl(src, ds)
  //
  //  println(r)
  //
  //  val x = {
  //    "idc;indosat;+33608995865;;;;en;1344939907;#902;U2;2;121;33999999999;;#902*#=37,1=1;".replaceAll("idc;indosat;\\+33608995865;;", "30")
  //  }
  //  println(s"x=$x")

  val x = List(("1", 2), ("1", 3), ("3", 3), ("6", 6)).groupBy(p => p._1).map(p => (p._1, p._2.map(x => x._2)))

  print(x)

}
