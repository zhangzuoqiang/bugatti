package controllers.conf

import models.conf
import models.conf._
import org.joda.time.DateTime
import play.api.Logger
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._
import play.api.libs.json._

/**
 * 配置文件
 */
object ConfController extends Controller {

  implicit val confWrites = Json.writes[Conf]
  implicit val contentWrites = Json.writes[ConfContent]

  def jobNo = "of546"

  val confForm = Form(
    mapping(
      "id" -> optional(number),
      "eid" -> number,
      "pid" -> number,
      "vid" -> number,
      "jobNo" -> ignored(jobNo),
      "name" -> optional(text),
      "path" -> nonEmptyText,
      "content" -> default(text, ""),
      "remark" -> optional(text),
      "updated" -> default(jodaDate("yyyy-MM-dd hh:mm:ss"), DateTime.now())
    )(ConfForm.apply)(ConfForm.unapply)
  )

  def show(id: Int) = Action {
    ConfHelper.findById(id) match {
      case Some(conf) =>
        Ok(Json.obj("conf" -> Json.toJson(conf), "content" -> ConfContentHelper.findById(id)))
      case None =>
        NotFound
    }
  }

  def all(eid: Int, vid: Int) = Action {
    Ok(Json.toJson(ConfHelper.findByEid_Vid(eid, vid)))
  }

  def delete(id: Int) = Action {
    Ok(Json.toJson(ConfHelper.delete(id)))
  }

  def save = Action { implicit request =>
    confForm.bindFromRequest.fold(
      formWithErrors => BadRequest(Json.obj("r" -> formWithErrors.errorsAsJson)),
      confForm => {
        Ok(Json.obj("r" -> Json.toJson(ConfHelper.create(confForm))))
      }
    )
  }

  def update(id: Int) = Action { implicit request =>
    confForm.bindFromRequest.fold(
      formWithErrors => BadRequest(Json.obj("r" -> formWithErrors.errorsAsJson)),
      confForm => {
        Ok(Json.obj("r" -> Json.toJson(ConfHelper.update(id, confForm))))
      }
    )
  }

  // ------------------------------------------------
  // 配置文件历史记录
  // ------------------------------------------------
  implicit val confLogWrites = Json.writes[ConfLog]
  implicit val confLogContentWrites = Json.writes[ConfLogContent]

  def logs(cid: Int, page: Int, pageSize: Int) = Action {
    Ok(Json.toJson(ConfLogHelper.allByCid(cid, page, pageSize)))
  }

  def logsCount(cid: Int) = Action {
    Ok(Json.toJson(ConfLogHelper.countByCid(cid)))
  }

  implicit def recordWrite = new Writes[(Option[ConfLog], Option[ConfLogContent])] {
    def writes(logType: (Option[ConfLog], Option[ConfLogContent])) = {
      Json.obj("log"-> logType._1,"logContent" -> logType._2)
    }
  }
  def log(id: Int) = Action {
    val log = ConfLogHelper.findById(id)
    val logContent = ConfLogContentHelper.findById(id)
    Ok(Json.toJson(log, logContent))
  }

  // ------------------------------------------------
  // 一键拷贝, todo 无事务，后期改造为队列
  // ------------------------------------------------
  case class CopyForm(target_eid: Int, target_vid: Int, eid: Int, vid: Int, ovr: Boolean)
  val copyForm = Form(
    mapping(
      "target_eid" -> number,
      "target_vid" -> number,
      "eid" -> number,
      "vid" -> number,
      "ovr" -> default(boolean, false)
    )(CopyForm.apply)(CopyForm.unapply)
  )
  def copy = Action { implicit request =>
    copyForm.bindFromRequest.fold(
      formWithErrors => BadRequest(Json.obj("r" -> formWithErrors.errorsAsJson)),
      copyForm => {
        if (copyForm.target_eid == copyForm.eid && copyForm.target_vid == copyForm.vid) {
          Ok(Json.obj("r" -> "exist"))
        } else {
          val targetConfs = ConfHelper.findByEid_Vid(copyForm.target_eid, copyForm.target_vid)
          val currConfs = ConfHelper.findByEid_Vid(copyForm.eid, copyForm.vid)
          val confs = copyForm.ovr match {
            case true =>
              targetConfs.filter(t => currConfs.map(_.path).contains(t.path)) foreach( c => ConfHelper.delete(c)) // delete exist
              targetConfs // return targets
            case false =>
              targetConfs.filterNot(t => currConfs.map(_.path).contains(t.path))
          }
          // insert all
          confs.foreach { c =>
            val content = ConfContentHelper.findById(c.id.get)
            val confForm = ConfForm(None, copyForm.eid, c.pid, copyForm.vid, c.jobNo, Some(c.name), c.path, if (content != None) content.get.content else "", c.remark, c.updated)
            ConfHelper.create(confForm)
          }
          Ok(Json.obj("r" -> "ok"))
        }
      }
    )

  }

}