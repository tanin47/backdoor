package tanin.backdoor.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import org.apache.hc.core5.reactor.Command;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.DatabaseUser;
import tanin.backdoor.core.Helpers;

import java.time.Instant;
import java.util.ArrayList;

public record AuthCookie(
  String backdoorUserId,
  String commandLineUserUsername,
  DatabaseUser[] databaseUsers,
  DatabaseConfig[] adHocDatabaseConfigs,
  Instant expires
) {
  public JsonValue toJson() {
    var json = Json.object();
    json.add("expires", expires.toEpochMilli());

    if (backdoorUserId != null) {
      json.add("backdoorUserId", backdoorUserId);
    }

    if (commandLineUserUsername != null) {
      json.add("commandLineUserUsername", commandLineUserUsername);
    }

    var databaseUsersJson = Json.array();
    if (databaseUsers != null) {
      for (var databaseUser : databaseUsers) {
        databaseUsersJson.add(databaseUser.toJson());
      }
    }

    json.add("databaseUsers", databaseUsersJson);

    var databaseConfigJsons = Json.array();
    for (var config : adHocDatabaseConfigs) {
      databaseConfigJsons.add(config.toJson());
    }

    json.add("adHocDatabaseConfigs", databaseConfigJsons);

    return json;
  }

  public static AuthCookie fromJson(JsonValue json) {
    var obj = json.asObject();
    var expires = Instant.ofEpochMilli(obj.get("expires").asLong());

    String backdoorUserId = Helpers.getString(obj, "backdoorUserId");
    String commandLineUserUsername = Helpers.getString(obj, "commandLineUserUsername");

    var databaseUsers = new ArrayList<DatabaseUser>();
    if (obj.get("databaseUsers") != null && !obj.get("databaseUsers").isNull()) {
      for (JsonValue jsonValue : obj.get("databaseUsers").asArray()) {
        databaseUsers.add(DatabaseUser.fromJson(jsonValue.asObject()));
      }
    }

    var adHocDatabaseConfigs = new ArrayList<DatabaseConfig>();
    if (obj.get("adHocDatabaseConfigs") != null) {
      for (JsonValue jsonValue : obj.get("adHocDatabaseConfigs").asArray()) {
        adHocDatabaseConfigs.add(DatabaseConfig.fromJson(jsonValue.asObject(), true));
      }
    }

    return new AuthCookie(
      backdoorUserId,
      commandLineUserUsername,
      databaseUsers.toArray(new DatabaseUser[0]),
      adHocDatabaseConfigs.toArray(new DatabaseConfig[0]),
      expires
    );
  }
}
