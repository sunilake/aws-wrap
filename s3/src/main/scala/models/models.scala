package aws.s3.models

import java.util.Date

import play.api.libs.ws._

import scala.concurrent.Future
import scala.xml.Elem

import aws.core._
import aws.core.Types._
import aws.core.parsers.Parser

import aws.s3.S3._
import aws.s3.S3.HTTPMethods._
import aws.s3.S3Parsers._

import scala.concurrent.ExecutionContext.Implicits.global

trait Http {

  import aws.s3.signature.S3Sign

  protected def ressource(bucketname: Option[String], uri: String, subresource: Option[String] = None) =
    "/%s\n%s\n?%s".format(bucketname.getOrElse(""), uri, subresource.getOrElse(""))

  protected def request(method: Method, bucketname: Option[String] = None, body: Option[String] = None, parameters: Seq[(String, String)] = Nil): Future[Response] = {
    val uri = bucketname.map("https://" + _ + ".s3.amazonaws.com").getOrElse("https://s3.amazonaws.com")
    val res = ressource(bucketname, uri)
    // TODO: do not hardcode contentType
    val r = WS.url(uri)
      .withHeaders(S3Sign.sign(method.toString, bucketname, contentType = body.map(_ => "text/plain; charset=utf-8")): _*)

    method match {
      case PUT => r.put(body.get)
      case DELETE => r.delete()
      case GET => r.get()
      case _ => throw new RuntimeException("Unsuported method: " + method)
    }
  }

  protected def tryParse[T](resp: Response)(implicit p: Parser[SimpleResult[T]]) = 
    Parser.parse[SimpleResult[T]](resp).fold( e => throw new RuntimeException(e), identity)
}

case class Bucket(name: String, creationDate: Date)
object Bucket extends Http {
  import Parameters._
  import Permisions._
  import ACLs._
  import Grantees._

  def createBucket(bucketname: String, acls: Option[ACL] = None, permissions: Seq[Grant] = Nil)(implicit region: AWSRegion): Future[EmptySimpleResult] = {
    val body =
      <CreateBucketConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
        <LocationConstraint>{ region.subdomain }</LocationConstraint>
      </CreateBucketConfiguration>

    val ps = acls.map(X_AMZ_ACL(_)).toSeq ++ permissions
    request(PUT, Some(bucketname), Some(body.toString), ps).map(tryParse[Unit])
  }

  def deleteBucket(bucketname: String): Future[EmptySimpleResult] =
    request(DELETE, Some(bucketname)).map(tryParse[Unit])

  def listBuckets(): Future[SimpleResult[Seq[Bucket]]] =
    request(GET).map(tryParse[Seq[Bucket]])
}