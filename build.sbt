lazy val baseName       = "ScalaCollider"
lazy val baseNameL      = baseName.toLowerCase
lazy val projectVersion = "1.28.4"
lazy val mimaVersion    = "1.28.0"   // for compatibility testing

lazy val deps = new {
  val main = new {
    val audioFile = "1.5.3"
    val osc       = "1.2.0"
    val optional  = "1.0.0"
    val processor = "0.4.2"
    val ugens     = "1.19.4"
  }
  val test = new {
    val scalaTest = "3.0.8-RC2"
  }
}

lazy val root = project.withId(baseNameL).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name                 := baseName,
    version              := projectVersion,
    organization         := "de.sciss",
    scalaVersion         := "2.12.8",
    crossScalaVersions   := Seq("2.12.8", "2.11.12", "2.13.0-RC2"),
    description          := "A sound synthesis library for the SuperCollider server",
    homepage             := Some(url(s"https://git.iem.at/sciss/${name.value}")),
    licenses             := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
    mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion),
    libraryDependencies ++= Seq(
      "de.sciss"      %% "scalaosc"                % deps.main.osc,
      "de.sciss"      %% "audiofile"               % deps.main.audioFile,
      "de.sciss"      %% "scalacolliderugens-core" % deps.main.ugens,
      "de.sciss"      %% "processor"               % deps.main.processor,
      "de.sciss"      %% "optional"                % deps.main.optional,
    ),
    libraryDependencies ++= {
      if (scalaVersion.value == "2.13.0-RC2") {
        Seq(
          "org.scalatest" % "scalatest_2.13.0-RC1" % deps.test.scalaTest % Test exclude("org.scala-lang.modules", "scala-xml_2.13.0-RC1")
        )
      } else {
        Seq(
          "org.scalatest" %% "scalatest" % deps.test.scalaTest % Test
        )
      }
    },
    scalacOptions in (Compile, compile) ++= {
      val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint:-stars-align,_")
      if (isSnapshot.value) xs else xs ++ Seq("-Xelide-below", "INFO")  // elide logging in stable versions
    },
    // ---- console ----
    initialCommands in console :=
      """import de.sciss.osc
        |import de.sciss.synth._
        |import de.sciss.synth.ugen._
        |import Predef.{any2stringadd => _}
        |import Ops._
        |def s = Server.default
        |def boot(): Unit = Server.run(_ => ())
        |""".stripMargin,
    // ---- build info ----
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt) => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq( (lic, _) )) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.synth"
  )
  .settings(publishSettings)

// ---- publishing ----
lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := { val n = name.value
<scm>
  <url>git@git.iem.at:sciss/{n}.git</url>
  <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
  }
)

