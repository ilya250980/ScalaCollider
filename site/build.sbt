lazy val baseName        = "ScalaCollider"
lazy val baseNameL       = baseName.toLowerCase

lazy val BASE_VERSION    = "1.28.0"
lazy val PROJECT_VERSION = BASE_VERSION

lazy val deps = new {
  val audioFile          = "1.5.0"
  val scalaColliderSwing = "1.41.0"
  val scalaColliderUGens = "1.19.0"
  val scalaOsc           = "1.2.0"
}

lazy val lScalaCollider       = RootProject(uri(s"git://github.com/Sciss/$baseName.git#v${BASE_VERSION}"))
lazy val lAudioFile           = RootProject(uri(s"git://github.com/Sciss/AudioFile.git#v${deps.audioFile}"))
lazy val lScalaColliderSwing  = RootProject(uri(s"git://github.com/Sciss/ScalaColliderSwing.git#v${deps.scalaColliderSwing}"))
lazy val lScalaColliderUGens  = RootProject(uri(s"git://github.com/Sciss/ScalaColliderUGens.git#v${deps.scalaColliderUGens}"))
lazy val lScalaOsc            = RootProject(uri(s"git://github.com/Sciss/ScalaOSC.git#v${deps.scalaOsc}"))

lazy val lList = Seq(lAudioFile, lScalaCollider, lScalaColliderUGens, lScalaColliderSwing, lScalaOsc)

scalaVersion in ThisBuild := "2.12.8"

lazy val unidocSettings = Seq(
  mappings in packageDoc in Compile := (mappings in (ScalaUnidoc, packageDoc)).value,
  scalacOptions in (Compile, doc) ++= Seq(
    "-skip-packages", Seq(
      "de.sciss.osc.impl", 
      "de.sciss.synth.impl",
      "snippets"
    ).mkString(":"),
    "-doc-title", s"${baseName} ${PROJECT_VERSION} API"
  )
) 

////////////////////////// site

val site = project.withId(s"$baseNameL-site").in(file("."))
  .enablePlugins(ParadoxSitePlugin, GhpagesPlugin, ScalaUnidocPlugin, SiteScaladocPlugin)
  .settings(unidocSettings)
  .settings(
    name                 := baseName, // IMPORTANT: `name` is used by GhpagesPlugin, must base base, not s"$baseName-Site"!
    version              := PROJECT_VERSION,
    siteSubdirName in SiteScaladoc    := "latest/api",
    git.remoteRepo       := s"git@github.com:Sciss/$baseName.git",
    git.gitCurrentBranch := "master",
    paradoxTheme         := Some(builtinParadoxTheme("generic")),
    paradoxProperties in Paradox ++= Map(
      "snippet.base_dir"        -> s"${baseDirectory.value}/snippets/src/main",
      "swingversion"            -> deps.scalaColliderSwing,
      "extref.swingdl.base_url" -> s"https://github.com/Sciss/ScalaColliderSwing/releases/download/v${deps.scalaColliderSwing}/ScalaColliderSwing_${deps.scalaColliderSwing}%s"
    )
  )
  .aggregate(lList: _*)

val snippets = project.withId(s"$baseNameL-snippets").in(file("snippets"))
  .dependsOn(lScalaCollider)
  .settings(
    name := s"$baseName-Snippets"
  )

////////////////////////// unidoc publishing

// In order to publish only the scala-docs coming out
// of sbt-unidoc, we must create an auxiliary module
// 'pub' depending on the aggregating module 'aggr'.
// There, we copy the setting of `packageDoc`. This way,
// we can publish using `sbt scalacollider-unidoc/publishLocal` etc.
// cf. https://github.com/sbt/sbt-unidoc/issues/65

lazy val aggr: Project = project.in(file("aggr"))
  .enablePlugins(ScalaUnidocPlugin)
  .settings(unidocSettings)
  .aggregate(lList: _*)

lazy val pub: Project = project.withId(s"$baseNameL-unidoc").in(file("pub"))
  .settings(publishSettings)
  .settings(
    name                  := s"$baseName-unidoc",
    version               := PROJECT_VERSION,
    organization          := "de.sciss",
    autoScalaLibrary      := false,
    licenses              := Seq("CC BY-SA 4.0" -> url("https://creativecommons.org/licenses/by-sa/4.0/")), // required for Maven Central
    homepage              := Some(url(s"https://git.iem.at/sciss/$baseName")),
    packageDoc in Compile := (packageDoc in Compile in aggr).value,
    publishArtifact in (Compile, packageBin) := false, // there are no binaries
    publishArtifact in (Compile, packageSrc) := false, // there are no sources
  )
 
lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo :=
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    ),
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
// N.B.: Bloody site plugin or ghpages already adds scm, then sonatype complains if
// we define it twice
//     <scm>
//      <url>git@git.iem.at:sciss/{n}.git</url>
//      <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
//    </scm>
  pomExtra := { val n = baseName
     <developers>
        <developer>
          <id>sciss</id>
          <name>Hanns Holger Rutz</name>
          <url>http://www.sciss.de</url>
        </developer>
      </developers>
  }
)

