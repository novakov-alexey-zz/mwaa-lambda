Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(ScalaJSBundlerPlugin, UniversalPlugin, LambdaJSPlugin)

name := "lambda"

scalaVersion := "2.13.7"
scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  // Explain type errors in more detail.
  "-explaintypes",
  // Warn when we use advanced language features
  "-feature",
  // Give more information on type erasure warning
  "-unchecked",
  // Enable warnings and lint
  "-Ywarn-unused",
  "-Xlint"
)
webpack / version := "4.46.0"
useYarn := false
webpackConfigFile := Some(baseDirectory.value / "webpack.config.js")
startWebpackDevServer / version := "3.1.4"

// Optional: Disable source maps to speed up compile times
scalaJSLinkerConfig ~= { _.withSourceMap(false) }

// Incluce type defintion for aws lambda handlers
val awsSdkVersion = "2.892.0"
val awsSdkScalajsFacadeVersion = s"0.33.0-v${awsSdkVersion}"
val http4sVersion = "0.23.7"
val natchezVersion = "0.1.6"

libraryDependencies ++= Seq(
  "org.typelevel" %% "feral-lambda" % "0.1.0-M1",
  "org.typelevel" %%% "feral-lambda-http4s" % "0.1.0-M1",
  "org.http4s" %%% "http4s-dsl" % http4sVersion,
  "org.http4s" %%% "http4s-server" % http4sVersion,
  "org.http4s" %%% "http4s-ember-client" % http4sVersion,
  "org.tpolecat" %%% "natchez-xray" % natchezVersion,
  "org.tpolecat" %%% "natchez-http4s" % "0.2.1",
  
  "org.scala-js" %%% "scala-js-macrotask-executor" % "1.0.0",
  // "org.scala-js" %%% "scalajs-dom" % "2.0.0",
  "io.bullet" %%% "borer-core" % "1.7.2",
  "io.bullet" %%% "borer-derivation" % "1.7.2",
  "net.exoego" %%% "aws-lambda-scalajs-facade" % "0.11.0",
  "net.exoego" %%% "aws-sdk-scalajs-facade-mwaa" % awsSdkScalajsFacadeVersion,
  "com.softwaremill.sttp.client3" %%% "core" % "3.3.18"
)

Compile / npmDependencies ++= Seq(
  "aws-sdk" -> awsSdkVersion
  // "abortcontroller-polyfill" -> "1.7.3",
  // "cross-fetch" -> "3.1.4",
  // "headers" -> "0.9.6"
)

// Optional: Include some nodejs types (useful for, say, accessing the env)
//libraryDependencies += "net.exoego" %%% "scala-js-nodejs-v12" % "0.14.0"

// Include scalatest
// libraryDependencies += "org.scalatest" %%% "scalatest" % "3.1.1" % "test"

// Package lambda as a zip. Use `universal:packageBin` to create the zip
topLevelDirectory := None
Universal / mappings ++= (Compile / fullOptJS / webpack).value.map { f =>
  // remove the bundler suffix from the file names
  f.data -> f.data.getName().replace("-opt-bundle", "")
}

// scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
scalaJSUseMainModuleInitializer := true
// scalacOptions ~= (_.filterNot(Set("-Wdead-code")))

npmPackageStage := org.scalajs.sbtplugin.Stage.FastOpt
