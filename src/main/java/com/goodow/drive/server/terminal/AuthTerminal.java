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

/**
 * @Author:DingPengwei
 * @Email:dingpengwei@goodow.com
 * @DateCrate:Apr 21, 2014 4:54:45 PM
 * @DateUpdate:Apr 21, 2014 4:54:45 PM
 * @Des:设备校验
 */
@GuiceVertxBinding(modules = {DriveModule.class})
public class AuthTerminal extends BusModBase {
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
   * @author:DingPengwei
   * @date:Apr 21, 2014 5:31:40 PM
   * @param rootMessage
   */
  private void saveDeviceActivity(final Message<JsonObject> rootMessage) {
    final JsonObject body = rootMessage.body();
    if (body.has("distance")) {
      double number = body.getNumber("distance");
      if (number > EFFECTIVE_DISTANCE) {
        rootMessage
            .reply(Json.createObject().set("status", "2").set("reset", "0").set("lock", "0"));
      } else {
        // 数据合格 更新当前地理信息
        JsonObject msg =
            Json.createObject().set("action", "index").set("_index", MyConstant.ES_INDEX).set(
                "_type", MyConstant.ES_TYPE_DEVICEACTIVITY).set("_id", body.getString("sid")).set(
                "source",
                Json.createObject().set("address", body.getString("address")).set(
                    "coordinates",
                    Json.createArray().push(body.getNumber("latitude")).push(
                        body.getNumber("longitude"))).set("radius", body.getNumber("radius")));

        bus.send("realtime.search", msg, new MessageHandler<JsonObject>() {
          @Override
          public void handle(Message<JsonObject> message) {
            rootMessage.reply(Json.createObject().set("status", "0").set("reset", "0").set("lock",
                "0"));
          }
        });
      }
    } else {
      if ((int) body.getNumber("errorcode") == 161) {// 客户端定位成功执行插入地理信息
        JsonObject msg =
            Json.createObject().set("action", "index").set("_index", MyConstant.ES_INDEX).set(
                "_type", MyConstant.ES_TYPE_DEVICEACTIVITY).set("_id", body.getString("sid")).set(
                "source",
                Json.createObject().set("address", body.getString("address")).set(
                    "coordinates",
                    Json.createArray().push(body.getNumber("latitude")).push(
                        body.getNumber("longitude"))).set("radius", body.getNumber("radius")));
        bus.send("realtime.search", msg, new MessageHandler<JsonObject>() {
          @Override
          public void handle(Message<JsonObject> messageDb) {
            rootMessage.reply(Json.createObject().set("status", "1").set("reset", "0").set("lock",
                "0"));
          }
        });
      } else {// 客户端定位失败
        rootMessage
            .reply(Json.createObject().set("status", "1").set("reset", "0").set("lock", "0"));
      }
    }
  }

  /**
   * 首次校验或者重置后首次校验
   * 
   * @author:DingPengwei
   * @date:Apr 21, 2014 5:15:28 PM
   * @param rootMessage
   */
  private void saveDeviceInfo(final Message<JsonObject> rootMessage) {
    JsonObject msg =
        Json.createObject().set("action", "index").set("_index", MyConstant.ES_INDEX).set("_type",
            MyConstant.ES_TYPE_DEVICE).set("_id", rootMessage.body().getString("sid")).set(
            "source", Json.createObject().set("owner", rootMessage.body().getString("schoolName")));
    bus.send("realtime.search", msg, new MessageHandler<JsonObject>() {
      @Override
      public void handle(Message<JsonObject> messageDb) {
        saveDeviceActivity(rootMessage);
      }
    });
  }
}