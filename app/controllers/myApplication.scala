
package controllers

import play.api.Play.current
import java.io.{File, InputStream}
import java.{lang, util}
import com.google.common.io.BaseEncoding
import play.api.libs.json._
import play.api.libs.oauth.{ServiceInfo, OAuth, OAuthCalculator, ConsumerKey}
import play.api.libs.ws._
import twitter4j.auth.{RequestToken, AccessToken, Authorization}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global

object App extends Controller {



  def index = Action.async{ request =>


    val urlToken="https://api.twitter.com/oauth2/token"
    val consumerKey="GSPRZwxpcJPArjkswi1Se16QQ"
    val consumerSecret="MPTQyCmB86yhHFg2egjmOO1pafw4vb0avdacWUe5uD6FvpycD3"
    val encodedKey=BaseEncoding.base64().encode((consumerKey+":"+consumerSecret).getBytes())

    val jType=Map(

      "grant_type"   -> Seq("client_credentials")
    )

    val postRequestForAuth: Future[WSResponse] =WS.url(urlToken).withHeaders("Authorization" -> s"Basic $encodedKey").post(jType)//postun içerisine map istyor key i string olsun valuesuda seq olarak istendi
    val futureResult: Future[Result] =postRequestForAuth.map{ (result: WSResponse) =>
      val token: String =result.json.\("access_token").asOpt[String].getOrElse("None")
      Redirect(routes.Application.test).withSession("token" -> token)
    }
    //val result = Await.result( postRequestForAuth, Duration( 10, "seconds"))
    // println("Result=> "+result.json)
    //val token =result.json.\("access_token").asOpt[String].getOrElse("None")
    //println("Access Token=> "+ token)



    futureResult
  }


  def test =Action.async{ request =>

    val token: String = request.session.get("token").getOrElse("None")
    println("Test içesinde token: "+token)
    val urlFriendList="https://api.twitter.com/1.1/friends/list.json?screen_name=twitterapi&skip_status=true"

    val friendListRequest: Future[WSResponse] =WS.url(urlFriendList).withHeaders(
      "Host" -> "api.twitter.com",
      "User-Agent" -> "emrekarakisApp",
      "Authorization" -> ("Bearer " + token)
    ).get()

    val futureRes: Future[Seq[String]] =friendListRequest.map{ (outcome: WSResponse) =>
      val jsonVal=outcome.json

      val seqOfName: Seq[JsValue] = (jsonVal \ "users").as[Seq[JsValue]]

      val returnVal: Seq[String] =seqOfName.map{ (each: JsValue) =>
        val stringVal=(each \ "name").as[String]
        stringVal
      }
      returnVal

    }
    /*
        val futureRes: Future[Seq[String]] =friendListRequest.map{ (outcome: WSResponse) =>
          val jsonVal: JsValue =outcome.json
          Logger.error(jsonVal.toString)
          val next_cursor_str: String =(jsonVal \ "next_cursor_str").as[String]
          val seqOfName: Seq[JsValue] = (jsonVal \ "users").as[Seq[JsValue]]

          val returnVal: Seq[String] =seqOfName.map{ (each: JsValue) =>
            val stringVal=(each \ "name").as[String]
            stringVal
          }
          returnVal
        }
         val rest=futureRes.map{ (x: Seq[String]) =>


        Ok(Json.toJson(x))
        }
        rest*/
    val rest=futureRes.map{ (x: Seq[String]) =>


      Ok(Json.toJson(x))
    }
    rest

    //val jsonVal=Await.result(friendListRequest , Duration(10,"seconds")).json
    //println("Res"+res.body)

    // val seqOfName: Seq[JsValue] = (jsonVal \ "users").as[Seq[JsValue]]

    /* val returnVal: Seq[String] =seqOfName.map{ (each: JsValue) =>
        val stringVal=(each \ "name").as[String]
          stringVal
     }*/



    /*  SendingRequest with access token
        val requestString: String = ConfigurationService.urlFriendList+"&cursor="+CursorVal
        val friendListRequest: Future[WSResponse] = WS.url(requestString).withHeaders(
          "Host" -> ConfigurationService.host,///option[string ]->string
          "User-Agent" -> ConfigurationService.getString("userAgent"),
          "Authorization" -> ("Bearer " + token)
        ).get()
        */
/*     wsresponse objesinden gerekli kısımları json verisinden parse ederek elde ettik ve next_cursor_str çok önemli
bir değerdi cunku her seferinde bir sonraki 20 kişiyi elde etmek için bu stringi TwitterResult olarak veriyorduk

Dahasonra bu twitteresult objesinden json değerlerini çekiyoruz next_cursor_stryi bir diziye atıp geri döndürüyoruz
      val futureReslt: Future[TwitterResult] = friendListRequest.map{ (out:WSResponse)=>
      val jsonVal: JsValue =out.json
      Logger.error(jsonVal.toString)
      val next_cursor_str: String =(jsonVal \ "next_cursor_str").as[String]
      val seqOfName: Seq[JsValue] = (jsonVal \ "users").as[Seq[JsValue]]
      val returnVal: Seq[String] =seqOfName.map{ (each: JsValue) =>
        val stringVal: String =(each \ "name").as[String]
        stringVal
      }
     val resVal: TwitterResult =TwitterResult(returnVal,next_cursor_str)

      resVal
    }
twitter result objesinden cursorı ve namesleri çektik ve json  olarak ekrana gönderdik
   val fItem = futureReslt.map{ item: TwitterResult=>
     val cursor: String =item.nextCursor
     val names: Seq[String] =item.names
     Ok(Json.toJson(names))

   }  /* en aşagıda kapandı

burada da tekrar aldıgımız cursorı ve tokenı  vererek tekrar request yaptık
    val req=futureReslt.map{ (a: TwitterResult) =>
    val curs: String =a.nextCursor
      val requestString2: String =ConfigurationService.urlFriendList+"&cursor="+curs

      val friendListRequest2: Future[WSResponse] =WS.url(requestString2).withHeaders(
        "Host" -> ConfigurationService.host,///option[string]->string
        "User-Agent" -> ConfigurationService.getString("userAgent"),
        "Authorization" -> ("Bearer " + token)
      ).get()

    }

  fItem  */*/


  }












  /* val cb = new ConfigurationBuilder
   cb.setDebugEnabled(true)
     .setOAuthConsumerKey("YOUR KEY HERE")
     .setOAuthConsumerSecret("YOUR SECRET HERE")
     .setOAuthAccessToken("YOUR ACCESS TOKEN")
     .setOAuthAccessTokenSecret("YOUR ACCESS TOKEN SECRET")
   val tf = new TwitterFactory(cb.build)
   val twitter = tf.getInstance


   val statuses = twitter.getFollowersList()
   println("Showing friends timeline.")
   val it = statuses.iterator
   while (it.hasNext()) {
     val status = it.ne
     println(status.getUser.getName + ":" + status.getText);*/
  //  WS.url(url).post(Results.EmptyContent())
  /*
private String EncodedKeys(consumerKeys:String , consumerSecret:String ){

    try{
        val encodedConsumerKey =URLEncoder.encode(consumerKey, "UTF-8")
        val  encodedConsumerSecret = URLEncoder.encode(consumerSecret, "UTF-8")

        val fullKey = encodedConsumerKey + ":" + encodedConsumerSecret

    }catch {

    }



}*/

  /*
  def namesConncetor(names:List[String]): List[String] ={
    if(names.isEmpty){
      throw new RuntimeException
    }else{
     val res= List.empty ++names
      res
    }

    }*/
}


