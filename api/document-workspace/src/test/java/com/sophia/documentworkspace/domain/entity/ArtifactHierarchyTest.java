package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.support.TestIds;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactHierarchyTest {

    private final UUID workspaceId = UUID.randomUUID();

    private Folder folder(Folder parent, String name) {
        return TestIds.withRandomId(Folder.create(workspaceId, parent, name,
                UUID.randomUUID(), null, null));
    }

    @Test
    void materializedPathsChainThroughAncestors() {
        Folder root = folder(null, "root");
        Folder child = folder(root, "child");
        Folder grandchild = folder(child, "grandchild");

        assertThat(root.getPath()).isEmpty();
        assertThat(child.getPath()).isEqualTo(root.getId() + ".");
        assertThat(grandchild.getPath()).isEqualTo(root.getId() + "." + child.getId() + ".");
        assertThat(grandchild.ancestorIds()).containsExactly(root.getId(), child.getId());
        assertThat(grandchild.resourceChain())
                .containsExactly(grandchild.getId(), root.getId(), child.getId());
    }

    @Test
    void descendantDetectionUsesThePath() {
        Folder root = folder(null, "root");
        Folder child = folder(root, "child");
        Folder sibling = folder(null, "sibling");

        assertThat(child.isDescendantOf(root)).isTrue();
        assertThat(root.isDescendantOf(child)).isFalse();
        assertThat(sibling.isDescendantOf(root)).isFalse();
    }

    @Test
    void moveToRewritesParentAndPath() {
        Folder root = folder(null, "root");
        Folder other = folder(null, "other");
        Folder child = folder(root, "child");

        child.moveTo(other);
        assertThat(child.getParentId()).isEqualTo(other.getId());
        assertThat(child.getPath()).isEqualTo(other.getId() + ".");

        child.moveTo(null);
        assertThat(child.getParentId()).isNull();
        assertThat(child.getPath()).isEmpty();
    }

    @Test
    void softDeleteAndRestoreLifecycle() {
        Folder folder = folder(null, "f");
        UUID deleter = UUID.randomUUID();
        assertThat(folder.isDeleted()).isFalse();

        folder.softDelete(deleter);
        assertThat(folder.isDeleted()).isTrue();
        assertThat(folder.getDeletedBy()).isEqualTo(deleter);

        folder.restore();
        assertThat(folder.isDeleted()).isFalse();
        assertThat(folder.getDeletedBy()).isNull();
    }
}
