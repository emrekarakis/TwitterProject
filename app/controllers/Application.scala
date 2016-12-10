package controllers
import java.util.{Date, Calendar}
import akka.dispatch.MessageDispatcher
import context.ExecutionContexts
import models.Models
import Models.TwitterResult
import play.api.Logger
import play.api.Play.current
import com.google.common.io.BaseEncoding
import play.api.libs.json._
import play.api.libs.ws._
import service.{GoogleService, ConfigurationService}
import twitter4j.auth.{RequestToken, AccessToken, Authorization}
import scala.concurrent.{ExecutionContext, Await, Future}
import play.api.mvc._
import scala.util.{Random, Failure, Success}

object Application extends Controller {

  val logger = Logger("ApplicationLogger")
  val encodedKey: String = BaseEncoding.base64().encode((ConfigurationService.consumerKey + ":" + ConfigurationService.consumerSecret).getBytes())
  val grantTypeMap: Map[String, Seq[String]] = Map("grant_type" -> Seq("client_credentials"))
  def index = Action.async { request =>

    val postRequestForAuth: Future[WSResponse] = WS.url(ConfigurationService.urlToken).withFollowRedirects(true)
      .withHeaders("Authorization" -> s"Basic $encodedKey").post(grantTypeMap)
    val futureTokenResult: Future[Result] = postRequestForAuth.map { (result: WSResponse) =>
      val resultMode: Int =result.status/100
      val simpleResult:Result = resultMode match {

        case 2 =>{
          val tokenAsOpt: Option[String] = (result.json \ "access_token").asOpt[String]
          val tokenResult: Result =tokenAsOpt match {
            case Some(token: String) => {
              Redirect(routes.Application.test).withSession("token" -> token)
            }
            case None => {
              logger.error("Could not get access token!")
              Ok("Could not get access token!")
            }
          }
          tokenResult
        }

        case _ =>{
          val errorMessagesAsOpt: Option[JsValue] = (result.json \ "errors").asOpt[JsValue]
          val nonExistentJsonCondition: Result= errorMessagesAsOpt match{
            case Some(json: JsValue) => {

              val errorValue: String = (json \ "message").asOpt[String].getOrElse("Json Object does not contain the subpart called 'message'")
              logger.error(s"An error has occured while getting token from Twitter. Coming Error Message from Twitter: ${errorValue } Status Text: ${result.statusText} Status Code: ${result.status}")
              Ok(s"An error has occured while getting token from Twitter. Coming Error Message from Twitter: ${errorValue } Status Text: ${result.statusText} Status Code: ${result.status}")
            }
            case None => {
              logger.error(s"Error message can not be displayed,because there is no json type object coming from Twitter.Result Object => ${result.json}")
              Ok(s"Error message can not be displayed,because there is no json type object coming from Twitter.Result Object => ${result.json}")
            }
          }
          nonExistentJsonCondition
        }
      }
      simpleResult
    }(ExecutionContexts.genericOps).recover {
      case e: Exception => {
        logger.error(s"An exception occurred while getting token from twitter", e)
        Ok(s"An exception occurred while getting token from twitter")
      }
    }(ExecutionContexts.genericOps)
    futureTokenResult
  }

  def test = Action.async { request =>

    val token: String = request.session.get("token").get
    val cursorVal = "-1"

    val comingValues: Future[Map[String, Seq[String]]] = concurrentSearcherAndIntegrator(token,cursorVal)
    val result: Future[Result] = comingValues.map{ (each: Map[String, Seq[String]]) =>
      Ok(Json.toJson(each))
    }(ExecutionContexts.genericOps).recover{
      case e: Exception => {
        logger.error(s"An exception occurred while getting token from twitter", e)
        Ok(s"An exception occurred while getting token from twitter")
      }
    }(ExecutionContexts.genericOps)
    result
  }

  def sendHttpRequestForFriendListToTwitter(token: String, cursorValue: String): Future[TwitterResult] = {
    val requestFriendListUrl: String = ConfigurationService.urlFriendList + "&cursor=" + cursorValue
    val friendListRequest: Future[WSResponse] = WS.url(requestFriendListUrl)
      .withHeaders(
        "Host" -> ConfigurationService.host,
        "User-Agent" -> ConfigurationService.getString("userAgent"),
        "Authorization" -> ("Bearer " + token)
      ).get

    val futureResult: Future[TwitterResult] = friendListRequest.map { (result: WSResponse) =>
      val resultMode: Int =result.status/100
      val jsonValue: JsValue = result.json

      val twitterResult: TwitterResult =resultMode match {
        case 2=>{
          val next_cursor_str: String = (jsonValue \ "next_cursor_str").asOpt[String].getOrElse("0")
          val sequenceOfNameAsOpt: Option[Seq[JsValue]] = (jsonValue \ "users").asOpt[Seq[JsValue]]
          val sequenceOfName: TwitterResult = sequenceOfNameAsOpt match {
              case Some(sequenceOfJson:Seq[JsValue]) => {
                val returnVal: Seq[String] = sequenceOfJson.map { (each: JsValue) =>
                  val followerName: String = (each \ "name").asOpt[String].getOrElse("")
                  followerName
                }
                val twitterResultPositive: TwitterResult = TwitterResult(returnVal, next_cursor_str)
                twitterResultPositive
              }
              case None => {
                logger.error(s"Couldn't be  parsed,because Json Object does not contain the subpart 'name'.Result: ${result.json}")
                val twitterResultNonJsonCase: TwitterResult = TwitterResult(Seq.empty,"0")
                twitterResultNonJsonCase
              }
            }
          sequenceOfName
        }
        case _=>{
          val errorMessagesAsOpt: Option[JsValue] = (jsonValue \ "errors").asOpt[JsValue]
          val errorCondition: Unit = errorMessagesAsOpt match {

            case Some(json: JsValue) => {
              val errorValue: String = (json \ "message").asOpt[String].getOrElse("Json Object does not contain the subpart called 'message'")
              logger.error(s"An error occurred while making request for user friendList to Twitter.Coming Error Message from Twitter: ${errorValue} Status-Text: ${result.statusText} Status Code: ${result.status}")
            }
            case None => {
              logger.error(s"Error message can not be displayed,there is no json type object coming from Twitter.Result Object => ${result.json}")
            }
          }

          val jsonErrorConditionForTwitterResult: TwitterResult = TwitterResult(Seq.empty,"0")
          jsonErrorConditionForTwitterResult
        }
      }
      twitterResult
    }(ExecutionContexts.genericOps)
    futureResult
  }

  def concurrentSearcherAndIntegrator(token: String, cursorValue: String): Future[Map[String, Seq[String]]] = {

    if (cursorValue == "0"){
      Future.successful {
        Map.empty
      }
    }else{

      val twitterResult: Future[TwitterResult] = sendHttpRequestForFriendListToTwitter( token, cursorValue)
      val combined: Future[ Map[String, Seq[String]]] = twitterResult.flatMap { ( each: TwitterResult ) =>
        val cursor: String = each.nextCursor
        val googleSearchResult: Seq[Future[Map[String, Seq[String]]]] = GoogleService.multiFollowerNameSearch(each.names)
        val futureOfGoogleSearchResult: Future[Seq[Map[String, Seq[String]]]] = Future.sequence(googleSearchResult)(implicitly, ExecutionContexts.genericOps)
        val listOfLink: Future[Map[String, Seq[String]]] = futureOfGoogleSearchResult.map { (each: Seq[Map[String, Seq[String]]]) =>

          val combinedValue: Map[String, Seq[String]] = each.foldLeft(Map.empty[String, Seq[String]]) { (k, v) =>
            combineMaps(k, v)
          }
          combinedValue
        }(ExecutionContexts.genericOps)
        val lastResult: Future[Map[String, Seq[String]]] = concurrentSearcherAndIntegrator(token, cursor)

        val combinedResults: Future[Map[String, Seq[String]]] =combineResults(lastResult,listOfLink)

        combinedResults
      }(ExecutionContexts.genericOps).recover{
        case e: Exception => {
          logger.error(s"An exception occurred while making http request for friendList to Twitter Token= ${token} CursorValue= ${cursorValue}", e)
           Map.empty[String,Seq[String]]
        }
      }(ExecutionContexts.genericOps)
      combined
    }
  }

  def combineResults(first:Future[Map[String,Seq[String]]],second:Future[Map[String,Seq[String]]]): Future[Map[String, Seq[String]]] ={
    implicit val genericOps: MessageDispatcher = ExecutionContexts.genericOps
    val output: Future[Map[String, Seq[String]]] =for {
      a <- first
      b <- second
    }yield a ++ b

    output

  }
  def combineMaps( map1: Map[String,Seq[String]], map2: Map[String,Seq[String]]):  Map[String,Seq[String]] = {
    try{
      val integratedMap: Map[String, Seq[String]] = (map1.keySet ++ map2.keySet).map{ e:String  =>
        e -> (map1.getOrElse(e,Seq.empty) ++ map2.getOrElse(e,Seq.empty))
      }.toMap
      integratedMap
    }catch {
      case e: Exception=> {
        println("An exception caught: ",e)
        Map.empty[String,Seq[String]]
      }
    }

  }
  /*
  def sendRequestForAccount(): Future[String] = {

    val postRequestForAccount: Future[WSResponse] = WS.url(ConfigurationService.urlUserAccount).withHeaders(
      "Authorization" ->
        s"""OAuth oauth_consumer_key=${ConfigurationService.consumerKey},
            oauth_nonce=705280ef116fd37435d385efc9d0b6dd,
            oauth_signature=CFvELMp6%2BVt%2BKa%2FqQgOfx3AuZzI%3D,
            oauth_signature_method=HMAC-SHA1,
            oauth_timestamp=1453202228,
            oauth_token=${ConfigurationService.oauthToken},
            oauth_version=1.0"""
    ).get()

    val futureResult: Future[String] =postRequestForAccount.map{ (each: WSResponse) =>
      val resultMode: Int = each.status/100
      val jsonVal: JsValue = each.json
      println("JSON"+jsonVal)
      val screenResult: String =resultMode match{
        case 2=>{
          val screenNameAsOpt: Option[String] = (jsonVal \ "screen_name").asOpt[String]
          val screenName: String =screenNameAsOpt match{
            case Some(scrnName: String)=>{
              scrnName
            }
            case None =>{
              logger.error("ScreenName could not be taken from Twitter")
              "0"
            }
          }

          screenName
        }
        case _=>{
          logger.error(s"An error has occured while getting screen name from Twitter=> Status Text: ${each.statusText} Status Code:${each.status} ")
          ""
        }
      }
      screenResult
    }(ExecutionContexts.genericOps)
    futureResult
  }*/


}
