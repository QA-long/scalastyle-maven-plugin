package org.scalastyle.maven.plugin

import com.typesafe.config.Config
import org.scalastyle._

import scala.xml.Elem

object HtmlOutput {
  def save[T <: FileSpec](config: Config, target: String, encoding: String, messages: Seq[Message[T]]): Unit =
    save(config, new java.io.File(target), encoding, messages)

  def save[T <: FileSpec](config: Config, target: String, encoding: String, messages: java.util.List[Message[T]]): Unit =
    save(config, new java.io.File(target), encoding, scala.collection.JavaConversions.collectionAsScalaIterable(messages))

  def save[T <: FileSpec](config: Config, target: java.io.File, encoding: String, messages: Iterable[Message[T]]): Unit = {
    val width = 1000
    println("============AAAAAA")
    val step = 1
    val messageHelper = new MessageHelper(config)

    val decl = """<?xml version="1.0" encoding="""" + encoding + """"?>"""
    val s = new XmlPrettyPrinter(width, step).format(toCheckstyleFormat(messageHelper, null))
    // scalastyle:off regex
    println("============刘保龙==========start==")
    println(s)
    println("============刘保龙==========end==")
//    printToFile(target, encoding) {
//      pw => pw.println(decl); pw.println(s)
//    }
    // scalastyle:on regex
  }

  private def printToFile(f: java.io.File, encoding: String)(op: java.io.PrintWriter => Unit): Unit = {
    val parent = f.getParentFile
    // sometimes f.getParentFile returns null - don't know why, but protect anyway
    if (parent != null && !parent.exists() && !parent.mkdirs()) { // scalastyle:ignore null
      throw new IllegalStateException("Couldn't create dir: " + parent)
    }

    val p = new java.io.PrintWriter(f, encoding)
    try {
      op(p)
    } catch {
      case e: Throwable => throw e
    } finally {
      p.close()
    }
  }

  case class Alert(filename: String, severity: String, message: String, source: Option[Class[_]], line: Option[Int], column: Option[Int])

  def toCheckstyleFormat[T <: FileSpec](messageHelper: MessageHelper, theMessages: java.util.List[Message[T]]): Elem = {
    import scala.collection.JavaConversions._
    val  messages: Iterable[Message[T]] = theMessages
    println("11111")
    <checkstyle version="5.0">
      {messages.collect {
      case StyleError(file, clazz, key, level, args, line, column, customMessage) =>{
        println(s"11111==StyleError file=${file.name}")
        Alert(file.name, messageHelper.text(level.name), Output.findMessage(messageHelper, key, args, customMessage), Some(clazz), line, column)
      }
      case StyleException(file, clazz, message, stacktrace, line, column) =>{
        println(s"11111==StyleException file=${file.name}")
        Alert(file.name, "error", message, clazz, line, column)
      }
    }.groupBy(x=>{
      println("222222")
      x.filename
    }).map(x=>{
      println(x._1+":"+x._2.size)
      x
    }).map {
      case (filename, alerts) =>
        <html name={filename}>
          {alerts.map {
          case Alert(fn, severity, message, source, line, column) => {
            val s = source.collect {
              case x: Class[_] => x.getName
            }
              <error severity={severity} message={message}/> % attr("source", s) % attr("line", line) % attr("column", column)
          }
        }}
        </html>
    }}
    </checkstyle>
  }

  private[this] def attr(name: String, value: Option[Any]): xml.MetaData = value match {
    case Some(x) => xml.Attribute("", name, x.toString, xml.Null)
    case None => xml.Null
  }
}
