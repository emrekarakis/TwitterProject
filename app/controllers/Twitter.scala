package controllers

import org.apache.http.client.methods.HttpGet
import play.api.Logger
import play.api.libs.oauth.{RequestToken, OAuth, ServiceInfo, ConsumerKey}
import play.api.mvc.{Result, Action, RequestHeader, Controller}

/**
 * Created by emrekarakis on 29/12/15.
 */
object Twitter extends Controller {

  val KEY = ConsumerKey("2xjMzii4SyZw8nRjinShewjsn", "MXZA3g58sJFd18K729gsiJYfOxP9D2XlwnMnfY2Dt9LRWpk2h3")

  val TWITTER= OAuth(ServiceInfo(
    "https://api.twitter.com/oauth/request_token",
    "https://api.twitter.com/oauth/access_token",
    "https://api.twitter.com/oauth/authorize", KEY),
    false)


  def authenticate = Action { request =>
    request.queryString.get("oauth_verifier").flatMap(_.headOption).map { verifier =>
      val tokenPair = sessionTokenPair(request).get
      // We got the verifier; now get the access token, store it and back to index
      TWITTER.retrieveAccessToken(tokenPair, verifier) match {
        case Right(t) => {
          // We received the authorized tokens in the OAuth object - store it before we proceed
          Logger.error("13123123123")
          Redirect(routes.Application.index).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      }
    }.getOrElse(
      TWITTER.retrieveRequestToken("http://localhost:9000/auth") match {
        case Right(t) => {
          // We received the unauthorized tokens in the OAuth object - store it before we proceed
          Logger.error("asdfasdfasdf")
          Redirect(TWITTER.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      })
  }

  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }
}