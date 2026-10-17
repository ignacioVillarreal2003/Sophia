package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceLabelRef;
import com.sophia.documentworkspace.domain.entity.WorkspaceLabel;
import com.sophia.documentworkspace.domain.entity.WorkspaceLabelLink;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLabelLinkRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The labels a user invents to organize their own workspace list. Nobody else can see or use them. */
@Service
@RequiredArgsConstructor
public class WorkspaceLabelService {

    static final int MAX_LABELS_PER_USER = 30;
    static final int MAX_LABELS_PER_WORKSPACE = 10;

    private final WorkspaceLabelRepository labelRepository;
    private final WorkspaceLabelLinkRepository linkRepository;
    private final WorkspaceAuthorizationService authorization;

    @Transactional(readOnly = true)
    public List<WorkspaceLabelRef> list(AuthenticatedUser user) {
        return labelRepository.findAllByUserIdOrderByNameAsc(user.id()).stream().map(this::toRef).toList();
    }

    @Transactional
    public WorkspaceLabelRef create(AuthenticatedUser user, String name, String color) {
        String clean = normalize(name);
        if (labelRepository.countByUserId(user.id()) >= MAX_LABELS_PER_USER) {
            throw new ConflictException("You can have up to %d labels".formatted(MAX_LABELS_PER_USER));
        }
        if (labelRepository.existsByUserIdAndNameIgnoreCase(user.id(), clean)) {
            throw new ConflictException("You already have a label called \"%s\"".formatted(clean));
        }
        return toRef(labelRepository.save(WorkspaceLabel.create(user.id(), clean, color)));
    }

    @Transactional
    public WorkspaceLabelRef update(AuthenticatedUser user, UUID labelId, String name, String color) {
        WorkspaceLabel label = load(user, labelId);
        String clean = name == null ? null : normalize(name);
        if (clean != null && !clean.equalsIgnoreCase(label.getName())
                && labelRepository.existsByUserIdAndNameIgnoreCase(user.id(), clean)) {
            throw new ConflictException("You already have a label called \"%s\"".formatted(clean));
        }
        label.update(clean, color);
        return toRef(label);
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID labelId) {
        // Links go with the label (ON DELETE CASCADE).
        labelRepository.delete(load(user, labelId));
    }

    /** Replaces the user's labels on one workspace with exactly this set. */
    @Transactional
    public List<WorkspaceLabelRef> assign(AuthenticatedUser user, UUID workspaceId, Collection<UUID> labelIds) {
        authorization.requireActiveMember(workspaceId, user);
        Set<UUID> wanted = new LinkedHashSet<>(labelIds == null ? List.of() : labelIds);
        if (wanted.size() > MAX_LABELS_PER_WORKSPACE) {
            throw new BadRequestException("A workspace can carry up to %d labels".formatted(MAX_LABELS_PER_WORKSPACE));
        }
        List<WorkspaceLabel> labels = labelRepository.findAllByIdInAndUserId(wanted, user.id());
        if (labels.size() != wanted.size()) {
            throw new ResourceNotFoundException("Label", wanted.stream().findFirst().orElse(null));
        }
        linkRepository.deleteForUserAndWorkspace(user.id(), workspaceId);
        linkRepository.flush();
        for (WorkspaceLabel label : labels) {
            linkRepository.save(WorkspaceLabelLink.of(label.getId(), workspaceId));
        }
        return labels.stream().map(this::toRef).sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }

    private WorkspaceLabel load(AuthenticatedUser user, UUID labelId) {
        return labelRepository.findByIdAndUserId(labelId, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Label", labelId));
    }

    private String normalize(String name) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            throw new BadRequestException("A label needs a name");
        }
        return clean;
    }

    private WorkspaceLabelRef toRef(WorkspaceLabel label) {
        return new WorkspaceLabelRef(label.getId(), label.getName(), label.getColor());
    }
}
