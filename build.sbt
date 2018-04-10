lazy val baseName       = "ScalaCollider"
lazy val baseNameL      = baseName.toLowerCase
lazy val projectVersion = "1.25.0-SNAPSHOT"
lazy val mimaVersion    = "1.25.0"   // for compatibility testing

name                 := baseName
version              := projectVersion
organization         := "de.sciss"
scalaVersion         := "2.12.5"
crossScalaVersions   := Seq("2.12.5", "2.11.12")

description          := "A sound synthesis library for the SuperCollider server"
homepage             := Some(url(s"https://github.com/Sciss/${name.value}"))
licenses             := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt"))

mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion)

lazy val deps = new {
  val main = new {
    val audioFile = "1.5.0-SNAPSHOT"
    val osc       = "1.1.6"
    val optional  = "1.0.0"
    val processor = "0.4.1"
    val ugens     = "1.18.0"
  }
  val test = new {
    val scalaTest = "3.0.5"
  }
}

libraryDependencies ++= Seq(
  "de.sciss"      %% "scalaosc"                % deps.main.osc,
  "de.sciss"      %% "scalaaudiofile"          % deps.main.audioFile,
  "de.sciss"      %% "scalacolliderugens-core" % deps.main.ugens,
  "de.sciss"      %% "processor"               % deps.main.processor,
  "de.sciss"      %% "optional"                % deps.main.optional,
  "org.scalatest" %% "scalatest"               % deps.test.scalaTest % "test"
//  "at.iem"        %% "scalacollider-dot"       % dotVersion       % "test"
)

scalacOptions ++= {
  val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint:-stars-align,_")
  if (isSnapshot.value) xs else xs ++ Seq("-Xelide-below", "INFO")  // elide logging in stable versions
}

// ---- console ----

initialCommands in console :=
"""import de.sciss.osc
  |import de.sciss.synth._
  |import de.sciss.synth.ugen._
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
