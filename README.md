![Demo](demo.png)

Modern and Embeddable Postgres Data Querying and Editing Tool
==============================================================

Backdoor is a small Java-based data querying and editing tool for Postgres and embeddable into your Java
application.

Its JAR is <2MB and doesn't have transitive dependencies (to avoid a dependency conflict). No need for a separate
instance nor deployment.

If you have a JVM-based website (using SpringBoot, PlayFramework, or others),
you can embed Backdoor and open up another port for it with just a few lines of code.

If you cannot serve it on another port, you can add a few more lines of code to proxy requests from a designated path to
Backdoor (See the "How to use" section).

Backdoor can handle large datasets with the use of virtual lists and infinite scrolling. It also supports running an
arbitrary SQL for more advanced needs of database administration.

Try it out today and let me know if it fits what you need!


Compared to the alternatives
-----------------------------

* Desktop apps like pgadmin and dbeaver require sharing the database credentials to everyone who wants to access the
  database. No activity history. No access control.
* Deployed apps like phpmyadmin and cloudbeaver require separate deployments and are huge/clunky.

Backdoor supports multiple users, activity history (pro feature), and access control (pro feature). It is small (<2MB)
and embeddable into your Java application. No separate deployment. No separate instance.

Please contact us if you are interested in the pro features.

How to use
-----------

There are 3 ways of using Backdoor:

1. Embed into your Java application and serve on a specific port.
2. Embed into your Java application and serve on your main port but at a specific path.
3. Run as a standalone.

### Embed and serve on a specific port

Add the dependency to your project:

```
<dependency>
    <groupId>io.github.tanin47</groupId>
    <artifactId>backdoor</artifactId>
    <version>1.0.0</version>
</dependency>
```

Then, initialize Backdoor when your Java application starts:

```
var server = new BackdoorServer(
    "postgres://backdoor_test_user:test@127.0.0.1:5432/backdoor_test", // The target database URL. Either JDBC or Postgres URL works.
    9999, // The port for Backdoor
    0, // The SSL port for Backdoor. Specify 0 if you don't need it
    "backdoor", // The username for accessing Backdoor
    "1234" // The password for accessing Backddor
);

server.start();
```

Then, when your Java application stops, make sure to stop Backdoor with:

```
server.stop();
```

### Embed and serve on a specific path

First, you must follow the steps above in order to serve Backdoor at a specific internal port.

Then, you can designate a specific path and use the below code to proxy requests to Backdoor:

```
var client = HttpClient.newHttpClient();
var httpRequest = HttpRequest
  .newBuilder()
  .uri(URI.create("http://localhost:9999" + path)) 
  .method("GET", HttpRequest.BodyPublishers.ofByteArray(new byte[0])) // Set the method and body in bytes
  .headers(/* ... */) // Forward the headers as-is.    
  .build();
var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
```

The above code uses the HTTP client offered by Java. It doesn't require any external dependency.

### Run as a standalone

First, you can download the JAR file from our Maven
repository: https://central.sonatype.com/artifact/io.github.tanin47/backdoor/overview

Then, you can run the command below:

```
java -jar backdoor-1.0.0.jar \
  -url postgres://backdoor_test_user:test@127.0.0.1:5432/backdoor_test \
  -port 9999 \
  -ssl-port 0 \
  -username backdoor \
  -password 1234
```

FAQ
-----

### How to configure the loggers?

Backdoor uses `java.util.logging`. The default logging config is at
`./src/main/java/resources/backdoor_default_logging.properties`.

If you have your own log config file, you can load it using:

```
try (var configFile = YourClass.class.getResourceAsStream("/YOUR_LOG_CONFIG_FILE")) {
  LogManager.getLogManager().readConfiguration(configFile);
  logger.info("The log config) has been loaded.");
} catch (IOException e) {
  logger.warning("Could not load the log config file: "+e.getMessage());
}
```

Contributing
==============

How to develop
---------------

1. Run `npm install` to install all dependencies.
2. Change the target database URL in `tanin.backdoor.BackdoorServier.main(..)`
2. Run `sbt run` in order to run the web server.
3. On a separate terminal, run `npm run hmr` in order to hot-reload the frontend code changes.

How to run tests
-----------------

1. Run `./setup/setup_db.sh` in order to set a test database.
1. Run `npm install` to install all dependencies.
3. On a separate terminal, run `npm run hmr`.
2. Run `sbt test` in order to run all the tests.

Publish
--------

1. Build the tailwindbase.css with:
   `./node_modules/.bin/postcss ./frontend/stylesheets/tailwindbase.css --config . --output ./src/main/resources/assets/stylesheets/tailwindbase.css`
2. Build the production Svelte code with:
   `ENABLE_SVELTE_CHECK=true ./node_modules/webpack/bin/webpack.js --config ./webpack.config.js --output-path ./src/main/resources/assets --mode production`
3. Build the fat JAR with: `sbt assembly`

The far JAR is built at `./target/scala-2.12/backdoor.jar`

You can run your server with: `java -jar backdoor.jar`

To publish to a Maven repository, please follow the below steps:

1. Go into the SBT console by running `sbt`
2. Switch to the fatJar project by running `project fatJar`
3. Run `publishSigned`
4. Run `sonaUpload`
5. Go to https://central.sonatype.com and click "Publish"
