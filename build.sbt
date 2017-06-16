lazy val baseName       = "ScalaCollider"
lazy val baseNameL      = baseName.toLowerCase
lazy val projectVersion = "1.22.4-SNAPSHOT"
lazy val mimaVersion    = "1.22.0"   // for compatibility testing

name                 := baseName
version              := projectVersion
organization         := "de.sciss"
scalaVersion         := "2.11.11"

// sbt 0.13.6 starts to upgrade Scala version!
// we must ensure 2.10.0 is used not 2.10.4
ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

// note SI-7436! Affects Scala throughout 2.10.x except for 2.10.0; might be fixed in 2.10.5
// https://issues.scala-lang.org/browse/SI-7436
// this implies that the Scala 2.10 version is compiled using Java 6,
// and the reason we do not add 2.12 here. One can simply build each
// cross version instead using `sbt ++2.12.1 test` etc.
crossScalaVersions   := Seq("2.11.11", "2.10.0")

description          := "A sound synthesis library for the SuperCollider server"
homepage             := Some(url(s"https://github.com/Sciss/${name.value}"))
licenses             := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt"))

mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion)

// ---- main dependencies ----

lazy val ugensVersion     = "1.16.4"
lazy val oscVersion       = "1.1.5"
lazy val audioFileVersion = "1.4.6"
lazy val processorVersion = "0.4.1"
lazy val optionalVersion  = "1.0.0"

// ---- test-only dependencies ----

lazy val scalaTestVersion = "3.0.3"
//lazy val dotVersion       = "0.4.0"

libraryDependencies ++= Seq(
  "de.sciss"      %% "scalaosc"                % oscVersion,
  "de.sciss"      %% "scalaaudiofile"          % audioFileVersion,
  "de.sciss"      %% "scalacolliderugens-core" % ugensVersion,
  "de.sciss"      %% "processor"               % processorVersion,
  "de.sciss"      %% "optional"                % optionalVersion,
  "org.scalatest" %% "scalatest"               % scalaTestVersion % "test"
//  "at.iem"        %% "scalacollider-dot"       % dotVersion       % "test"
)

scalacOptions ++= {
  val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture")
  val ys = if (scalaVersion.value.startsWith("2.10")) xs else xs :+ "-Xlint:-stars-align,_"  // syntax not supported in Scala 2.10
  if (isSnapshot.value) ys else ys ++ Seq("-Xelide-below", "INFO")  // elide logging in stable versions
}

// ---- console ----

initialCommands in console :=
"""import de.sciss.osc
  |import de.sciss.synth._
  |import ugen._
  |import Predef.{any2stringadd => _}
  |import Ops._
  |def s = Server.default
  |def boot(): Unit = Server.run(_ => ())
  |""".stripMargin

// ---- build info ----

enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
  BuildInfoKey.map(homepage) { case (k, opt) => k -> opt.get },
  BuildInfoKey.map(licenses) { case (_, Seq( (lic, _) )) => "license" -> lic }
)

buildInfoPackage := "de.sciss.synth"

// ---- publishing ----

publishMavenStyle := true

publishTo :=
  Some(if (isSnapshot.value)
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
}
