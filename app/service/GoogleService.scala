package service

import java.net.URLEncoder

import context.ExecutionContexts
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.libs.ws.{WS, WSResponse}
import scala.concurrent.Future
/**
 * Created by emrekarakis on 11/01/16.
 */
object GoogleService{

  val logger = Logger("ApplicationLogger")

 def multiFollowerNameSearch(comingSequence: Seq[String]): Seq[Future[Map[String, Seq[String]]]] ={
      val futureValue: Seq[Future[Map[String, Seq[String]]]] = comingSequence.map{ (followerName: String) =>
      val searchResultFuture: Future[Map[String, Seq[String]]] = followerNameSearch(followerName)
      val searchResult: Future[Map[String, Seq[String]]] = searchResultFuture.recover {
        case e: Exception => {
          logger.error(s"Exception occurred while searching from google, follower name: ${followerName}", e)
          Map.empty[String, Seq[String]]
        }
      }(ExecutionContexts.genericOps)
      searchResult
    }
   futureValue
 }

  def followerNameSearch(nameToSearch:String): Future[Map[String, Seq[String]]] ={

    val searchResult:Future[WSResponse] =
      try{
      val key: String =URLEncoder.encode(ConfigurationService.apiKey, "UTF-8")
      val customSearchID: String =URLEncoder.encode(ConfigurationService.customSearchEngineID, "UTF-8")
      val query: String =URLEncoder.encode(nameToSearch, "UTF-8")
      val alt: String =URLEncoder.encode(ConfigurationService.altValue,"UTF-8")
      val organizedSearchUrl: String = s"""${ConfigurationService.urlCustomSearch}key=${key}&cx=${customSearchID}&q=${query}&alt=${alt}"""
      val searchRequestToGoogleApi: Future[WSResponse] = WS.url(organizedSearchUrl).get
      searchRequestToGoogleApi
    }catch{
      case e: Exception => {
        println("An exception caught ",e)
        Future.failed(e)
      }
    }

    val futureResult: Future[Map[String, Seq[String]]] = searchResult.map { (result: WSResponse) =>
      val resultMode=result.status/100
      val jsonValue: JsValue = result.json

      val mapResult: Map[String, Seq[String]] = resultMode match{
        case 2=>{
          val sequenceOfLinkAsOpt: Option[Seq[JsValue]] = (jsonValue \ "items").asOpt[Seq[JsValue]]
          val sequenceOfLinkAsPerSearchString: Map[String, Seq[String]] = sequenceOfLinkAsOpt match {
            case Some(sequenceOfJson: Seq[JsValue]) => {
                val returnValue: Seq[String] = sequenceOfJson.map { (each: JsValue) =>
                val stringValue: String = (each \ "link").asOpt[String].getOrElse("")
                stringValue
              }
              Map(nameToSearch ->returnValue)
            }
            case None=>{
              logger.error(s"Could not be parsed,because Json Object does not contain the subpart called 'link'.Result: ${result.json}")
              Map.empty[String,Seq[String]]
            }
          }
          sequenceOfLinkAsPerSearchString
        }
        case _=>{

          val errorMessagesAsOpt: Option[JsValue] = (jsonValue \ "error").asOpt[JsValue]
          val nonExistentMessageCondition: Unit = errorMessagesAsOpt match {
            case Some(json: JsValue) => {
              val errorValue: String = (json \ "message").asOpt[String].getOrElse("Json Object does not contain the subpart called 'message'")
              logger.error(s"An error has occured while getting links from Google.Coming Error Message from Google: ${errorValue} Status Text: ${result.statusText} Status Code: ${result.status}")
            }
            case None => {
              logger.error(s"Error message can not be displayed,because there is no json type object coming from Google.Result: ${result.json}")
             }
           }
          Map(""->Seq.empty)
        }
      }
      mapResult
    }(ExecutionContexts.genericOps)

    futureResult
    }

 }



