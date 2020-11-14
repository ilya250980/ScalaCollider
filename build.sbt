lazy val baseName       = "ScalaCollider"
lazy val baseNameL      = baseName.toLowerCase
lazy val projectVersion = "2.4.0"
lazy val mimaVersion    = "2.4.0"   // for compatibility testing

lazy val deps = new {
  val main = new {
    val audioFile = "2.3.0"
    val osc       = "1.2.3"
    val optional  = "1.0.1"
    val processor = "0.5.0"
    val serial    = "2.0.0"
    val ugens     = "1.20.0"
  }
  val test = new {
    val scalaTest = "3.2.3"
  }
}

lazy val commonJvmSettings = Seq(
  crossScalaVersions   := Seq("3.0.0-M1", "2.13.3", "2.12.12"),
)

lazy val root = crossProject(JVMPlatform, JSPlatform).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(commonJvmSettings)
  .settings(
    name                 := baseName,
    version              := projectVersion,
    organization         := "de.sciss",
    scalaVersion         := "2.13.3",
    description          := "A sound synthesis library for the SuperCollider server",
    homepage             := Some(url(s"https://git.iem.at/sciss/${name.value}")),
    licenses             := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
    mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion),
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

