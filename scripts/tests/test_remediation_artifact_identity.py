"""Reject source/artifact/runtime/image drift; a dirty local artifact is not a release."""
import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile
from scripts.remediation_artifact_identity import artifact_manifest, require_runtime_identity, require_image_identity, require_release_identity


class ArtifactIdentityTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory();self.addCleanup(self.directory.cleanup)
        self.jar=Path(self.directory.name)/"fixture.jar"
        self.snapshot={"source_commit":"c"*40,"source_hash":"a"*64,"dirty":True,"build_id":"fixture-build",
                       "source_files":{"src/main/resources/db/migration/tenant/V1__fixture.sql":hashlib.sha256(b"fixture").hexdigest()}}
        self.metadata="build.version=1.0.0\nbuild.source.commit="+"c"*40+"\nbuild.source.hash="+"a"*64+"\nbuild.source.dirty=true\nbuild.id=fixture-build\n"
        self.pack()
    def pack(self, metadata=None, migration=b"fixture", layout="BOOT-INF/classes/META-INF/build-info.properties"):
        with zipfile.ZipFile(self.jar,"w") as jar:
            jar.writestr(layout, self.metadata if metadata is None else metadata)
            jar.writestr("BOOT-INF/classes/db/migration/tenant/V1__fixture.sql",migration)
    def test_packaged_identity_and_migration_bytes_match(self):
        manifest=artifact_manifest(self.jar,self.snapshot)
        self.assertEqual(hashlib.sha256(self.jar.read_bytes()).hexdigest(),manifest["jar_sha256"])
        self.assertFalse(manifest["deployment_verified"])
    def test_boot_root_metadata_and_ambiguous_layout(self):
        self.pack(layout="META-INF/build-info.properties")
        artifact_manifest(self.jar,self.snapshot)
        with zipfile.ZipFile(self.jar,"a") as jar:
            jar.writestr("BOOT-INF/classes/META-INF/build-info.properties",self.metadata)
        with self.assertRaises(ValueError):artifact_manifest(self.jar,self.snapshot)
        with zipfile.ZipFile(self.jar,"w") as jar:
            jar.writestr("unrelated.properties",self.metadata)
        with self.assertRaises(ValueError):artifact_manifest(self.jar,self.snapshot)
    def test_missing_metadata_wrong_revision_and_migration_drift_fail(self):
        for metadata,migration in (("",b"fixture"),(self.metadata.replace("fixture-build","wrong-build"),b"fixture"),(self.metadata,b"rewritten")):
            with self.subTest(metadata=metadata,migration=migration):
                self.pack(metadata,migration)
                with self.assertRaises(ValueError):artifact_manifest(self.jar,self.snapshot)
    def test_runtime_identity_must_match_every_packaged_field(self):
        manifest=artifact_manifest(self.jar,self.snapshot)
        response={"version":"1.0.0","sourceCommit":"c"*40,"sourceHash":"a"*64,"buildId":"fixture-build","dirty":True}
        require_runtime_identity(manifest,response)
        for field in response:
            with self.assertRaises(ValueError):require_runtime_identity(manifest,{**response,field:"wrong"})
    def test_wrong_or_unlabelled_running_image_fails(self):
        manifest={**artifact_manifest(self.jar,self.snapshot),"image_id":"sha256:fixture"}
        labels={"org.opencontainers.image.revision":"c"*40,"org.opencontainers.image.version":"1.0.0",
                "io.logisticsx.build.id":"fixture-build","io.logisticsx.source.hash":"a"*64,"io.logisticsx.source.dirty":"true"}
        image={"Id":"sha256:fixture","Config":{"Labels":labels}}
        require_image_identity(manifest,image,{"Image":"sha256:fixture"})
        with self.assertRaises(ValueError):require_image_identity(manifest,image,{"Image":"sha256:old"})
        with self.assertRaises(ValueError):require_image_identity(manifest,{**image,"Config":{}},{"Image":"sha256:fixture"})
    def test_dirty_local_build_and_absent_image_never_certify_release(self):
        manifest=artifact_manifest(self.jar,self.snapshot)
        with self.assertRaises(ValueError):require_release_identity(manifest)
        with self.assertRaises(ValueError):require_release_identity({**manifest,"dirty":False})
        release={**manifest,"dirty":False,"image_id":"sha256:reviewed","openapi_canonical_sha256":"b"*64}
        require_release_identity(release)
        for field in ("source_hash","openapi_canonical_sha256"):
            with self.assertRaises(ValueError):require_release_identity({**release,field:"UNKNOWN"})
