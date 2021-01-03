import com.typesafe.tools.mima.core.{Problem, ProblemFilters}

lazy val baseName       = "ScalaCollider"
lazy val baseNameL      = baseName.toLowerCase
lazy val projectVersion = "2.4.2-SNAPSHOT"
lazy val mimaVersion    = "2.4.0"   // for compatibility testing

lazy val deps = new {
  val main = new {
    val audioFile = "2.3.2"
    val osc       = "1.2.4-SNAPSHOT"
    val optional  = "1.0.1"
    val processor = "0.5.0"
    val serial    = "2.0.0"
    val ugens     = "1.20.1"
  }
  val test = new {
    val scalaTest = "3.2.3"
  }
}

lazy val commonJvmSettings = Seq(
  crossScalaVersions   := Seq("3.0.0-M2", "2.13.4", "2.12.12"),
)

// sonatype plugin requires that these are in global
ThisBuild / version      := projectVersion
ThisBuild / organization := "de.sciss"

lazy val root = crossProject(JVMPlatform, JSPlatform).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(commonJvmSettings)
  .settings(
    name                 := baseName,
//    version              := projectVersion,
//    organization         := "de.sciss",
    scalaVersion         := "2.13.4",
    description          := "A sound synthesis library for the SuperCollider server",
    homepage             := Some(url(s"https://git.iem.at/sciss/${name.value}")),
    licenses             := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
    mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[Problem]("de.sciss.synth.impl.*"),
    ),
    libraryDependencies ++= Seq(
      "de.sciss"      %%% "audiofile"               % deps.main.audioFile,
      "de.sciss"      %%% "optional"                % deps.main.optional,
      "de.sciss"      %%% "processor"               % deps.main.processor,
      "de.sciss"      %%% "scalacolliderugens-core" % deps.main.ugens,
      "de.sciss"      %%% "scalaosc"                % deps.main.osc,
      "de.sciss"      %%% "serial"                  % deps.main.serial,
      "org.scalatest" %%% "scalatest"               % deps.test.scalaTest % Test,
    ),
    scalacOptions in (Compile, compile) ++= {
      val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xlint:-stars-align,_", "-Xsource:2.13")
      val elide = !isSnapshot.value && !isDotty.value
      val ys = if (!elide) xs else xs ++ Seq("-Xelide-below", "INFO")  // elide logging in stable versions
      val sv = scalaVersion.value
      if (sv.startsWith("2.13.")) ys :+ "-Wvalue-discard" else ys
    },
    sources in (Compile, doc) := {
      if (isDotty.value) Nil else (sources in (Compile, doc)).value // dottydoc is hopelessly broken
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
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  developers := List(
    Developer(
      id    = "sciss",
      name  = "Hanns Holger Rutz",
      email = "contact@sciss.de",
      url   = url("https://www.sciss.de")
    )
  ),
  scmInfo := {
    val h = "git.iem.at"
    val a = s"sciss/${name.value}"
    Some(ScmInfo(url(s"https://$h/$a"), s"scm:git@$h:$a.git"))
  },
)

