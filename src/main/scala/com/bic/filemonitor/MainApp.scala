package com.bic.filemonitor

import akka.actor.ActorSystem
import com.beachape.filemanagement.MonitorActor
import com.beachape.filemanagement.RegistryTypes._
import com.beachape.filemanagement.Messages._

import java.io.{FileWriter, BufferedWriter}

import java.nio.file.{CopyOption, Paths, Files}
import java.nio.file.StandardWatchEventKinds._
import scala.collection.GenTraversableOnce

import scala.util.Random

/**
 * Created by patrick.jiang on 7/2/14.
 * directory structure
 * /opt/bic/raw/cc_cdr/rawdata
 * - /cg_cdr
 * - /huawei_cdr
 * - /subms_cdr
 * - /subms_snapshot
 * - /cc_stats
 * - /cg_stats
 */
object MainApp extends App {

//  val sparkConf = new SparkConf()
//    .setMaster("local[*]")
//    .setAppName("etl")
//  val sc = new SparkContext(sparkConf)

  implicit val system = ActorSystem("actorSystem")
  val fileMonitorActor = system.actorOf(MonitorActor(concurrency = 20))

  val createCallbackDirectory: Callback = {
    path => {
      println(s"event -> $path")
      println(s"size -> ${path.getParent.toFile.listFiles().size}")
      if (path.getParent.toFile.listFiles().size >= 10) {
        path.getParent.toFile.listFiles().foreach(f=>{
          val target = Paths.get(f.getAbsolutePath.replaceAll("raw","in"))
          println(s"target-> $target")
          println(s"move-> from ${f.toPath} to $target")
          Files.move(f.toPath, target)
        })
      }
    }
  }

  val rawDir = Paths get "/tmp/fmtest/raw"
  val inDir = Paths get "/tmp/fmtest/in"
  val outDir = Paths get "/tmp/fmtest/out"
  val propsDir = Paths get "/tmp/fmtest/props"

  /*
    If desktopFile is modified, this will also receive a callback
    it will receive callbacks for everything under the desktop directory
  */
  fileMonitorActor ! RegisterCallback(
    ENTRY_CREATE,
    recursive = false,
    path = rawDir,
    callback = createCallbackDirectory)

  Thread.sleep(Random.nextInt(1000))

  List.range(1, 100,1).toList.foreach(p=>{
//    println(p)
    val t = Paths.get(s"/tmp/fmtest/raw/$p")
    t.toFile.createNewFile()
  })

}