package org.fireflyframework.ecm.adapter.docusign;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocuSignAuthenticationBoundaryTest {
    @TempDir Path temporary;
    @Test void cancelledAuthenticationCannotPublishOrContinueAfterAnInterruptIsConsumed() throws Exception {
        IndependentAuthCancellationProbe.main(new String[]{temporary.resolve("cancel.service").toString()});
    }
    @Test void successfulOwnedCleanupStillConsumesTheOriginalAuthenticationDeadline() throws Exception {
        IndependentAuthCloseDeadlineProbe.main(new String[]{temporary.resolve("close.service").toString()});
    }
    @Test void allocationAcrossCompletedClientClosureIsClosedAndRefused() throws Exception {
        IndependentClientBuildCloseProbe.main(new String[]{temporary.resolve("build.service").toString()});
    }
}
