name         := "scalacollider"
version      := "0.34"
organization := "de.sciss"
scalaVersion := "2.11.8"
description  := "A sound synthesis library for the SuperCollider server"
homepage     := Some(url("https://github.com/Sciss/ScalaCollider"))
licenses     := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))

libraryDependencies ++= Seq(
   "de.sciss"       %% "scalaosc"       % "0.33",
   "de.sciss"       %% "scalaaudiofile" % "0.20",
   "org.scala-lang" % "scala-actors"    % scalaVersion.value,
   "org.scalatest"  %% "scalatest"      % "2.1.3" % "test"
)

scalacOptions ++= Seq("-deprecation", "-unchecked")

// ---- console ----

initialCommands in console := """import de.sciss.osc; import de.sciss.synth.{ osc => sosc, _ }; import ugen._; var s: Server = null; def boot = Server.run( s = _ )"""

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

pomExtra :=
<scm>
  <url>git@github.com:Sciss/ScalaCollider.git</url>
  <connection>scm:git:git@github.com:Sciss/ScalaCollider.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>

