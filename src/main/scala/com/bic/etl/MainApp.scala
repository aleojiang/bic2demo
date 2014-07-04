package com.bic.etl

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}
import java.util.Properties

import org.apache.spark.SparkContext._
import org.apache.spark.{Logging, SparkConf, SparkContext}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods.parse

import scala.io.Source

/**
 * Created by patrick.jiang on 7/4/14.
 *
 */
object MainApp extends App with Logging {

  val commonProperties = new Properties()
  val cpFile = Source.fromFile("/home/patrick.jiang/opensources/bic2demo/src/main/resources/cccdr.properties").reader()
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

  step(rawDir, step1)
  step(confDir, step2)
  step(inDir, step3)

  def step(root: String, fun: (String, String) => Unit) = {
    Paths.get(root).toFile.listFiles().filter(_.isDirectory)
      .map(f => f.getName -> f.listFiles().filter(_.isDirectory).map(_.getAbsolutePath))
      .map(kv => kv._2.foreach(v => fun(kv._1, v)))
  }

  def step1(from: String, sources: String) = {
    logError(s"source=$sources")
    Paths.get(sources).toFile.listFiles().foreach(f => {
      f.getName.endsWith(".properties") match {
        case true =>
          val target = f.getAbsolutePath.replaceAll(rawDir, confDir)
          Files.move(f.toPath, Paths.get(target))
        case _ =>
          val target = f.getAbsolutePath.replaceAll(rawDir, inDir)
          Files.move(f.toPath, Paths.get(target))
      }
    })
  }

  def step2(from: String, sources: String) = {
    new File(sources).listFiles()
      .filter(_.getName.endsWith(".properties")).foreach(f => {
      val pattern = propPattern.r
      val pattern(op) = f.getName
      val jfn = "fields"
      val prop = new Properties()
      val inp = new FileInputStream(f)
      prop.load(inp)
      inp.close()
      val ast = parse(prop.getProperty(jfn))
      var index = 0
      ast transform {
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
    implicit val tps = Paths.get(confPath).toFile.listFiles().map(f => {
      val prop = new Properties()
      prop.load(new FileInputStream(f))
      new CDRFormatter(prop)
    })
    if (Paths.get(inPath).toFile.listFiles().length > 0) {
      val rawData = sc.wholeTextFiles(inPath)
        .map(kv => kv._2.split("\n").map(v => {
        val pattern = cdrPattern.r
        val pattern(op) = kv._1
        s"$from;$op;$v"
      }))
        .flatMap(p => p)
      val dimUser = sc.textFile("").map(p=>(p,p))
      var latestUserId = dimUser.sortByKey(ascending = true,1).collect().last._1
      val newFoundUsers = rawData.map(updateUserDim)
        .leftOuterJoin(dimUser).filter(p=>p._2._2.isEmpty).collect()
      newFoundUsers foreach {
        case (k, (value,None)) =>
          latestUserId += 1
          val line = s"$value;$latestUserId\n"
        case _ =>
      }

      val dimPage = sc.textFile("").map(p=>(p,p))
      var latestPageId = dimPage.sortByKey(ascending = true,1).collect().last._1
      val newFoundPages = rawData.map(updatePageDim).flatMap(p=>p).map(p=>(p,1))
        .leftOuterJoin(dimPage).filter(p=>p._2._2.isEmpty).collect()
      newFoundPages foreach {
        case (k, (value,None)) =>
          latestPageId += 1
          val line = s"$value;$latestPageId\n"
        case _ =>
      }

      rawData.map(updateUserDim).reduceByKey(_ + _, 1).saveAsTextFile("/tmp/12")
      rawData.map(updatePageDim).flatMap(p=>p).map(p=>(p,1)).reduceByKey(_ + _, 1).saveAsTextFile("/tmp/13")
    }
  }

  def chooseFormatter(text: String)(implicit tps: Array[CDRFormatter]) = {
    logError(s"$text")
    tps.find(prop => {
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


  def updateUserDim(text: String)(implicit tps: Array[CDRFormatter]) = {
    val formatter = chooseFormatter(text)
    val delimiter = formatter.delimiter
    val from = text.split(delimiter)(0)
    val op = text.split(delimiter)(1)
    val msisdn = formatter.getFieldValue(text, "msisdn").getOrElse("")
    val imsi = formatter.getFieldValue(text, "imsi").getOrElse("")
    val imei = formatter.getFieldValue(text, "imei").getOrElse("")
    val key = List(from, op, msisdn, imsi, imei).mkString(delimiter)
    val value = 1
    (key, value)
  }

  def updatePageDim(text: String)(implicit tps: Array[CDRFormatter]) = {
    val formatter = chooseFormatter(text)
    val delimiter = formatter.delimiter
    val from = text.split(delimiter)(0)
    val op = text.split(delimiter)(1)
    val pages = formatter.getFieldValue(text, "action_url").getOrElse("").toString
    pagePattern.r.findAllMatchIn(pages).map(p=>s"$from$delimiter$op$delimiter$p").toList
  }
}


class CDRFormatter(properties: Properties) extends Serializable {
  val tsPattern = "yyyy-MM-dd HH:mm:SS"
  val tsName = "format_start_date_time"

  def getProperties = properties

  def delimiter = properties.getProperty("delimiter", ";")

  def getTs = DateTime.parse(properties.getProperty(tsName), DateTimeFormat.forPattern(tsPattern)).getMillis

  def getSessionTime(text: String, key: String, aggPattern: Option[String] = None) = {
    val ttt = properties.getProperty(s"$key.index")
    val index = properties.getProperty(s"$key.index").toInt + 2
    val delimiter = properties.getProperty(s"delimiter")
    val tp = properties.getProperty(s"$key.type").toUpperCase
    val fmt = properties.getProperty(s"$key.format")
    val ovs = text.split(delimiter)(index)
    try {
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
    val index = properties.getProperty(s"$key.index").toInt + 2
    val delimiter = properties.getProperty(s"delimiter")
    val tp = properties.getProperty(s"$key.type").toUpperCase
    val ovs = text.split(delimiter)(index)
    try {
      Some(tp match {
        case "INT" => ovs.toInt
        case "LONG" => ovs.toLong
        case _ => ovs
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
  val pages = "#702*#=file:/mcel/xumii/twitter/en/twitter_login.xml#id_entry_input_pincode,1234=file:/mcel/xumii/twitter/en/twitter_login.xml#main_menu,2=file:/mcel/xumii/twitter/en/twitter_post_tweet.xml,1=file:/mcel/xumii/twitter/en/twitter_post_tweet.xml#id87,hello everyone2012-08-16T08:02:38=file:/mcel/xumii/twitter/en/twitter_post_tweet.xml#id90,9=file:/mcel/xumii/twitter/en/twitter_login.xml#main_menu,1=file:/mcel/xumii/twitter/en/twitter_timeline.xml#timeline_brief,1=file:/mcel/xumii/twitter/en/twitter_timeline.xml#id12"
  val p = "(file:[^\\,\r\n?]+|https?:[^\\,\r\n?]+)"
  val xx = p.r.findAllMatchIn(pages).toList
  println(xx)

}
