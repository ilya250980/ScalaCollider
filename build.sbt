name               := "ScalaCollider"
version            := "1.20.0"
organization       := "de.sciss"
scalaVersion       := "2.11.8"

// sbt 0.13.6 starts to upgrade Scala version!
// we must ensure 2.10.0 is used not 2.10.4
ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

// note SI-7436! Affects Scala throughout 2.10.x except for 2.10.0; might be fixed in 2.10.5
// https://issues.scala-lang.org/browse/SI-7436
crossScalaVersions := Seq("2.11.8", "2.10.0")

description        := "A sound synthesis library for the SuperCollider server"
homepage           := Some(url(s"https://github.com/Sciss/${name.value}"))
licenses           := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt"))

// ---- main dependencies ----

lazy val ugensVersion     = "1.15.2"
lazy val oscVersion       = "1.1.5"
lazy val audioFileVersion = "1.4.5"
lazy val processorVersion = "0.4.0"
lazy val optionalVersion  = "1.0.0"

// ---- test-only dependencies ----

lazy val scalaTestVersion = "3.0.0"

libraryDependencies ++= Seq(
  "de.sciss"      %% "scalaosc"                % oscVersion,
  "de.sciss"      %% "scalaaudiofile"          % audioFileVersion,
  "de.sciss"      %% "scalacolliderugens-core" % ugensVersion,
  "de.sciss"      %% "processor"               % processorVersion,
  "de.sciss"      %% "optional"                % optionalVersion,
  "org.scalatest" %% "scalatest"               % scalaTestVersion % "test"
)

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture")

scalacOptions ++= {
  if (isSnapshot.value) Nil else Seq("-Xelide-below", "INFO")  // elide logging in stable versions
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
