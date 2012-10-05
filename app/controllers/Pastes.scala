package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import akka.actor.Props
import com.olegych.scastie.{PastesActor, PastesContainer}
import akka.pattern.ask
import akka.util.duration._
import play.api.libs.concurrent._
import akka.util.Timeout
import play.api.Play
import java.io.File
import play.api.templates.Html
import com.olegych.scastie.PastesActor.{GetPaste, Paste, AddPaste}


object Pastes extends Controller {

  import play.api.Play.current
  import concurrent.ExecutionContext.Implicits.global

  val pastesDir = new File(Play.configuration.getString("pastes.data.dir").getOrElse("./target/pastes/"))
  val renderer = Akka.system.actorOf(Props(new PastesActor(PastesContainer(pastesDir))), "pastes")

  implicit val timeout = Timeout(100 seconds)

  val pasteForm = Form(
    single(
      "paste" -> text
    )
  )

  def add = Action { implicit request =>
    Async {
      val paste = pasteForm.bindFromRequest().apply("paste").value.get
      (renderer ? AddPaste(paste)).mapTo[Paste].asPromise.map { paste =>
        Redirect(routes.Pastes.show(paste.id))
      }
    }
  }

  def show(id: Long) = Action { implicit request =>
    Async {
      (renderer ? GetPaste(id)).mapTo[Paste].asPromise.map { paste =>
        val content = paste.content.getOrElse("")
        val output = paste.output.getOrElse("")
        val typedContent = if (content.matches("(?mis)\\s*<pre>.*")) Left(Html(content)) else Right(content)
        val ref = """\[(?:error|warn)\].*test.scala:(\d+)""".r
        val highlights = ref.findAllIn(output).matchData.map(_.group(1).toInt).toSeq
        Ok(views.html.show(typedContent, output, highlights))
      }
    }
  }
}
