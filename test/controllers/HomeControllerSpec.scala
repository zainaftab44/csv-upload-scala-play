package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._
import play.api.mvc.MultipartFormData
import java.io.File

/** Add your spec here. You can mock out a whole application including requests, plugins etc.
  *
  * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
  */
class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "HomeController GET" should {
    
    "upload file correctly" in {
      val controller = new HomeController(stubControllerComponents())
     
      val fileName = "/test/test.csv"
      val file = new File(fileName)
      val data = MultipartFormData(Map(), List(MultipartFormData.FilePart("csv[]", fileName, Some("text/csv"), file)), List())
      val result = controller.upload().apply(FakeRequest(POST, "/").withBody(data))
      
    }
  }
}
