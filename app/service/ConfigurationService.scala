package service

import play.api.Play
import play.api.Play.current
/**
 * Created by emrekarakis on 04/01/16.
 */
object ConfigurationService {
  val host: String =getString("twitterHost")
  val consumerSecret: String =ConfigurationService.getString("consumerSecret")
  val urlToken: String =ConfigurationService.getString("urlToken")
  val consumerKey: String =ConfigurationService.getString("consumerKey")
  val oauthToken: String = ConfigurationService.getString("oauthToken")
  val urlFriendList: String =ConfigurationService.getString("urlFriendList")
  val apiKey: String= ConfigurationService.getString("apiKey")
  val urlCustomSearch :String=ConfigurationService.getString("urlCustomSearch")
  val customSearchEngineID:String = ConfigurationService.getString("customSearchEngineId")
  val altValue:String=ConfigurationService.getString("alt")
  def getString(key:String):String={
    val readValueAsOpt: Option[String] = Play.application.configuration.getString(key)
    val readValueFromConf: String= readValueAsOpt match {
      case Some(x) => {
        x
      }
      case None => {
       new RuntimeException(s" could not get ${key} from configuration file")
        ""
      }
    }
    readValueFromConf
  }
  def getStringAsOpt(key: String): Option[String] = {
    Play.application.configuration.getString(key)
  }

}
