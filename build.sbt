ThisBuild / scalaVersion := "3.3.7"
ThisBuild / organization := "com.mindmaps"

lazy val root = (project in file("."))
  .settings(
    name := "mind-maps-backend",
    // zio-http 3.9.0 requires zio-schema 1.8.2; zio-jdbc 0.1.1 requires 0.4.14.
    // The higher version (1.8.2) is selected — allow the eviction.
    libraryDependencySchemes += "dev.zio" %% "zio-schema" % VersionScheme.Always,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"             % "2.1.24",
      "dev.zio" %% "zio-streams"     % "2.1.24",
      "dev.zio" %% "zio-http"        % "3.9.0",
      "dev.zio" %% "zio-jdbc"        % "0.1.1",
      "com.h2database" % "h2"        % "2.4.240",
      "com.auth0"      % "java-jwt"  % "4.5.1",
      "org.mindrot"    % "jbcrypt"   % "0.4",
      // 0.9.0 is what zio-http 3.9.0 transitively requires via zio-schema-json
      "dev.zio" %% "zio-json"        % "0.9.0",
      "dev.zio" %% "zio-test"        % "2.1.24" % Test,
      "dev.zio" %% "zio-test-sbt"    % "2.1.24" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
