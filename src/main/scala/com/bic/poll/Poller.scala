package com.bic.poll

import java.io.{IOException, File}
import java.nio._
import java.nio.file._

import com.decodified.scalassh._
import com.typesafe.config.{ConfigList, ConfigValue, ConfigFactory, Config}
import net.schmizz.sshj.xfer.FileSystemFile

import scala.io.Source

/**
 * Created by patrick.jiang on 7/2/14.
 */
object Poller extends App {
//  val pollerLocation = "/opt/bic/poller/application.conf"
//  val conf: Config = ConfigFactory.parseFile(new File(pollerLocation))
//
//  ConfigFactory.invalidateCaches()
//  val isTerminated = conf.getBoolean("global.flag.termination")
//  val dcs = conf.getList("datacenters.names").toArray
//    .foreach(dc => {
//    val temp1 = dc.asInstanceOf[ConfigValue]
//    val dtypes = conf.getList(s"datacenters.${temp1.unwrapped()}.datatypes").toArray
//      .foreach(dt => {
//      val temp2 = dt.asInstanceOf[ConfigValue]
//      val nodes = conf.getList(s"datacenters.${temp1.unwrapped()}.${temp2.unwrapped()}.hosts").toArray
//        .foreach(node => {
//        val temp3 = node.asInstanceOf[ConfigValue]
//        println(conf.getString(s"datacenters.${temp1.unwrapped()}.${temp2.unwrapped()}.${temp3.unwrapped()}.name"))
//        println(conf.getString(s"datacenters.${temp1.unwrapped()}.${temp2.unwrapped()}.${temp3.unwrapped()}.src"))
//        println(conf.getString(s"datacenters.${temp1.unwrapped()}.${temp2.unwrapped()}.${temp3.unwrapped()}.dest"))
//      })
//    })
//  })
//
//
//  val cccdr: ConfigList = conf.getList("datacenters.dc1.cccdr.hosts")
//
//
//  cccdr.toArray.foreach(p => {
//    val x: ConfigValue = p.asInstanceOf[ConfigValue]
//    println(conf.getString(s"datacenters.dc1.cccdr.${x.unwrapped()}.name"))
//    println(conf.getString(s"datacenters.dc1.cccdr.${x.unwrapped()}.src"))
//    println(conf.getString(s"datacenters.dc1.cccdr.${x.unwrapped()}.dest"))
//
//  })
//
//  if (!isTerminated) {
//    println(cccdr)
//    //      preData
//    //      StatsTasks.process()
//    Thread sleep 1000
//  }
//
  nodePolling("cncduldt011")
  def nodePolling(host: String) = {
    SSH(host, HostResourceConfig()) {
      client=>
        val scp = client.client.newSCPFileTransfer().newSCPDownloadClient()
        scp.setRecursiveMode(true)
        scp.copy("/opt/data/*", new FileSystemFile("/tmp/11/"))
    }
  }


}


