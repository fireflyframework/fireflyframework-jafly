package org.fireflyframework.ecm.adapter.docusign;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelopeDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SignatureEnvelopeSnapshotBoundsTest {
    @TempDir Path temporary;
    @Test void tenMiBPerDocumentBoundaryIsPreservedWithoutUnboundedCopying() throws Exception {
        byte[] maximum = new byte[10*1024*1024];
        var value = DocuSignImmutableContentTest.snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, maximum);
        assertEquals(maximum.length, value.contentLength());
        byte[] tooLarge = new byte[maximum.length+1];
        assertThrows(IllegalArgumentException.class, () -> DocuSignImmutableContentTest.snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, tooLarge));
    }
    @Test void totalContentBeyondTwentyMiBIsRefusedBeforeEncodingOrSdkIo() throws Exception {
        try (var fixture = new DocuSignImmutableContentTest.Fixture(temporary)) {
            byte[] content = new byte[7*1024*1024]; var ids = new ArrayList<UUID>(); var values = new ArrayList<SignatureEnvelopeDocument>();
            for (int i=0;i<3;i++) { var id=new UUID(0,i+1);ids.add(id);values.add(DocuSignImmutableContentTest.snapshot(id,content)); }
            var envelope=DocuSignEnvelopeMappingTest.envelope().toBuilder().documentIds(ids).build();
            assertThrows(DocuSignAdapterException.class, () -> DocuSignImmutableContentTest.await(fixture.port.createEnvelope(envelope,values))); fixture.noIo();
        }
    }
}
