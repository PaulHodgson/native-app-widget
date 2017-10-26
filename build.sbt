import TestPhases.oneForkedJvmPerTest
import play.sbt.routes.RoutesKeys._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, _}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

name := "native-app-widget"

lazy val root = (project in file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(PlayKeys.playDefaultPort := 8247)

routesImport ++= Seq("uk.gov.hmrc.domain._", "uk.gov.hmrc.nativeappwidget.binders.Binders._")
publishingSettings
unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"
defaultSettings()

scalaVersion := "2.11.11"
crossScalaVersions := Seq("2.11.11")

PlayKeys.playDefaultPort := 8247

libraryDependencies ++= AppDependencies.appDependencies
retrieveManaged := true
evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
routesGenerator := StaticRoutesGenerator

Keys.fork in IntegrationTest := false
unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest)(base => Seq(base / "it")).value
addTestReportOption(IntegrationTest, "int-test-reports")
testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value)
parallelExecution in IntegrationTest := false

resolvers ++= Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.jcenterRepo
)
