package com.scalawit

import dispatch._
import play.api.libs.json.{Writes, Json, Reads}
import dispatch.Req
import scala.concurrent.ExecutionContext.Implicits.global
import javax.sound.sampled.{AudioFormat, AudioInputStream}
import com.ning.http.client.generators.InputStreamBodyGenerator

class Client(token: String) {
  val witHost = :/(Config.DOMAIN)
  private val defaultHeaders = Map(
    "Content-type" -> "application/json",
    "Authorization" -> s"Bearer ${token}",
    "Accept" -> s"application/vnd.wit.${Config.API_VERSION}"
  )
  private val audioEncodingNames = Map(
    AudioFormat.Encoding.ALAW -> "a-law",
    AudioFormat.Encoding.PCM_SIGNED -> "signed-integer",
    AudioFormat.Encoding.PCM_UNSIGNED -> "unsigned-integer",
    AudioFormat.Encoding.ULAW -> "mu-law"
  )

  def httpGet[T](path: Seq[String], params: Map[String, String] = Map())(implicit reads: Reads[T]): Future[Either[WitError, T]] = {
    httpRequest[T](makeUrl(path) <<? params)
  }

  def httpGetRaw(path: Seq[String], params: Map[String, String] = Map()): Future[Either[WitError, String]] = {
    httpRequestRaw(makeUrl(path) <<? params)
  }

  def httpPost[T](path: Seq[String], data: T)(implicit writes: Writes[T]): Future[Either[WitError, String]] = {
    httpRequestRaw(makeUrl(path).POST << Json.toJson(data).toString)
  }

  def httpPut[T](path: Seq[String], data: T)(implicit writes: Writes[T]): Future[Either[WitError, String]] = {
    httpRequestRaw(makeUrl(path).PUT << Json.toJson(data).toString)
  }

  def httpDelete(path: Seq[String]): Future[Either[WitError, String]] = {
    httpRequestRaw(makeUrl(path).DELETE)
  }

  def httpPostAudio[T](stream: AudioInputStream)(implicit reads: Reads[T]): Future[Either[WitError, T]] = {
    sendAudioRequest[T](stream, httpRequest[T] _)
  }

  def httpPostAudioRaw(stream: AudioInputStream): Future[Either[WitError, String]] = {
    sendAudioRequest[String](stream, httpRequestRaw _)
  }

  private def sendAudioRequest[T](stream: AudioInputStream, f: (Req, Map[String,String]) => Future[Either[WitError, T]]): Future[Either[WitError, T]] = {
    val format = stream.getFormat()
    val encoding = audioEncodingNames.get(format.getEncoding()).getOrElse(return Future(Left(WitMalformedRequestError("unsupported audio encoding"))))
    val bits = format.getSampleSizeInBits()
    if (bits != 8 && bits != 16 && bits != 32) return Future(Left(WitMalformedRequestError("unsupported sample size")))
    val rate = format.getSampleRate().toInt
    val endian = if (format.isBigEndian()) "big" else "little"
    val headers = Map(
      "Content-type" -> s"audio/raw;encoding=${encoding};bits=${bits};rate=${rate};endian=${endian}",
      "Authorization" -> s"Bearer ${token}",
      "Accept" -> s"application/vnd.wit.${Config.API_VERSION}"
    )
    val request = makeUrl(Seq("speech")).POST.underlying(_.setBody(new InputStreamBodyGenerator(stream)))
    f(request, headers)
  }

  private def httpRequestRaw(request: Req, headers: Map[String, String] = defaultHeaders): Future[Either[WitError, String]] = {
    val completeRequest = request <:< headers secure
    val resultFut = Http(completeRequest OK as.String).either
    resultFut.map { _.fold(ex => Left(WitError.getWitError(ex)), Right(_)) }
  }

  private def httpRequest[T](request: Req, headers: Map[String, String] = defaultHeaders)(implicit reads: Reads[T]): Future[Either[WitError, T]] = {
    val completeRequest = request <:< headers secure
    val resultFut = Http(completeRequest OK as.String).either
    resultFut.map {
      _.fold(ex => Left(WitError.getWitError(ex)),
        content => Json.parse(content).asOpt[T].map(Right(_)).getOrElse(Left(WitResponseParsingError(content))))
    }
  }

  private def makeUrl(path: Seq[String]): Req = path.foldLeft(witHost)(_ / _)
}
