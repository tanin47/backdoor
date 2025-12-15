package tanin.backdoor.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import org.apache.hc.core5.reactor.Command;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.DatabaseUser;

import java.time.Instant;
import java.util.ArrayList;

public record AuthCookie(
  BackdoorUser backdoorUser,
  CommandLineUser commandLineUser,
  DatabaseUser[] databaseUsers,
  DatabaseConfig[] adHocDatabaseConfigs,
  Instant expires
) {
  public JsonValue toJson() {
    var json = Json.object();
    json.add("expires", expires.toEpochMilli());

    if (backdoorUser != null) {
      json.add("backdoorUser", backdoorUser.toJson());
    }

    if (commandLineUser != null) {
      json.add("commandLineUser", commandLineUser.toJson());
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

    BackdoorUser backdoorUser = null;
    if (obj.get("backdoorUser") != null && !obj.get("backdoorUser").isNull()) {
      backdoorUser = BackdoorUser.fromJson(obj.get("backdoorUser"));
    }

    CommandLineUser commandLineUser = null;
    if (obj.get("commandLineUser") != null && !obj.get("commandLineUser").isNull()) {
      commandLineUser = CommandLineUser.fromJson(obj.get("commandLineUser"));
    }

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
      backdoorUser,
      commandLineUser,
      databaseUsers.toArray(new DatabaseUser[0]),
      adHocDatabaseConfigs.toArray(new DatabaseConfig[0]),
      expires
    );
  }
}
