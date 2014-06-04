package com.goodow.drive.server.terminal;

import com.goodow.drive.server.bootstrap.DriveModule;
import com.goodow.drive.server.utils.MyConstant;
import com.goodow.realtime.channel.Bus;
import com.goodow.realtime.channel.Message;
import com.goodow.realtime.channel.MessageHandler;
import com.goodow.realtime.json.Json;
import com.goodow.realtime.json.JsonArray;
import com.goodow.realtime.json.JsonObject;

import com.google.inject.Inject;

import com.alienos.guice.GuiceVerticleHelper;
import com.alienos.guice.GuiceVertxBinding;

import org.vertx.java.busmods.BusModBase;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * @Author:DingPengwei
 * @Email:dingpengwei@goodow.com
 * @DateCrate:Apr 21, 2014 4:54:45 PM
 * @DateUpdate:Apr 21, 2014 4:54:45 PM
 * @Des:设备校验
 */
@GuiceVertxBinding(modules = {DriveModule.class})
public class AuthTerminal extends BusModBase {
  private static Logger log = Logger.getLogger(AuthTerminal.class.getName());
  @Inject
  private Bus bus;
  JsonArray searchHit;
  private static final int EFFECTIVE_DISTANCE = 50; // 设备移动的有效距离

  @Override
  public void start() {
    GuiceVerticleHelper.inject(this, vertx, container);
    super.start();
    bus.registerHandler(MyConstant.ADDR_GEO, new MessageHandler<JsonObject>() {
      @Override
      public void handle(Message<JsonObject> rootMessage) {
        JsonObject body = rootMessage.body();
        if (body.has("schoolName")) {
          // 首次校验或者重置后首次校验
          saveDeviceInfo(rootMessage);
        } else {
          // 常规校验
          saveDeviceActivity(rootMessage);
        }
      }
    });
  }

  /**
   * 常规校验
   *
   * @param rootMessage
   * @author:DingPengwei
   * @date:Apr 21, 2014 5:31:40 PM
   */
  private void saveDeviceActivity(final Message<JsonObject> rootMessage) {
    final JsonObject body = rootMessage.body();
    if (body.has("distance")) {
      double number = body.getNumber("distance");
      if (number > EFFECTIVE_DISTANCE) {
        rootMessage
            .reply(Json.createObject().set("status", 2).set("reset", false).set("lock", false),null);
      } else {
        //上线成功 发广播
        publishMsgFun(body.getString("sid"), body.getNumber("latitude"), body.getNumber(
            "longitude"), body.getNumber("radius"));
        rootMessage.reply(Json.createObject().set("status", 0).set("reset", false).set("lock",
            false),null);
      }
    } else {
      if ((int) body.getNumber("errorcode") == 161) {// 客户端定位成功执行插入地理信息
        rootMessage.reply(Json.createObject().set("status", 1).set("reset", false).set("lock",
            false),null);
      } else {// 客户端定位失败
        rootMessage
            .reply(Json.createObject().set("status", 1).set("reset", false).set("lock", false),null);
      }
    }
  }

  /**
   * 首次校验或者重置后首次校验
   *
   * @param rootMessage
   * @author:DingPengwei
   * @date:Apr 21, 2014 5:15:28 PM
   */
  private void saveDeviceInfo(final Message<JsonObject> rootMessage) {
    JsonObject msg =
        Json.createObject()
            .set("action", "index")
            .set("_index", MyConstant.ES_INDEX)
            .set("_type", MyConstant.ES_TYPE_DEVICE)
            .set("_id", rootMessage.body().getString("sid"))
            .set("source", Json.createObject()
                //设备编码.这里需要前台传递
                .set("code",UUID.randomUUID().toString())
                .set("owner", rootMessage.body().getString("schoolName"))
                .set("registAddress", rootMessage.body().getString("address"))
                .set("registCoordinates", Json.createArray()
                    .push(rootMessage.body().getNumber("latitude"))
                    .push(rootMessage.body().getNumber("longitude")))
                .set("radius", rootMessage.body().getNumber("radius"))
                .set("reset", 0)
                .set("lock", 0));
    log.info("###########device insert########+"+msg.toString());
    bus.send("realtime.search", msg, new MessageHandler<JsonObject>() {
      @Override
      public void handle(Message<JsonObject> messageDb) {
        log.info("insert sucess will reply");
          //首次校验不应发送数据到deviceActivity
        rootMessage.reply(Json.createObject().set("status", 0).set("reset", false).set("lock", false),null);
      }
    });
  }

  /**
   * 安卓设备上线发送广播
   *
   * @param deviceId  设备ID
   * @param latitude  纬度
   * @param longitude 经度
   * @param radius    精度
   */
  private void publishMsgFun(final String deviceId, final double latitude, final double longitude,
                             final double radius) {
    //封装数据
    JsonObject obj = Json.createObject().set("deviceId", deviceId).set(
        "coordinates", Json.createArray().push(longitude).push(latitude)).set("radius", radius)
        .set("status","login");
    //发送广播
    bus.publish(MyConstant.DEVICE_STATUS, obj);
  }

}
