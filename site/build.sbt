val scalaColliderVersion = "1.22.2"

val lOSC           = RootProject(uri( "git://github.com/Sciss/ScalaOSC.git#v1.1.4"))
val lAudioFile     = RootProject(uri( "git://github.com/Sciss/ScalaAudioFile.git#v1.4.5"))
val lUGens         = RootProject(uri( "git://github.com/Sciss/ScalaColliderUGens.git#v1.16.3"))
val lScalaCollider = RootProject(uri(s"git://github.com/Sciss/ScalaCollider.git#v$scalaColliderVersion"))

val root = (project in file("."))
  .settings(unidocSettings)
  .enablePlugins(ParadoxSitePlugin, SiteScaladocPlugin)
  .settings(ghpages.settings)
  .settings(
    name                 := "ScalaCollider",
    version              := scalaColliderVersion,
    siteSubdirName in SiteScaladoc    := "latest/api",
    mappings in packageDoc in Compile := (mappings  in (ScalaUnidoc, packageDoc)).value,
    git.remoteRepo       := s"git@github.com:Sciss/${name.value}.git",
    git.gitCurrentBranch := "master",
    paradoxTheme         := Some(builtinParadoxTheme("generic")),
    scalacOptions in (Compile, doc) ++= Seq("-skip-packages", "de.sciss.osc.impl")
  )
  .aggregate(lOSC, lAudioFile, lUGens, lScalaCollider)
