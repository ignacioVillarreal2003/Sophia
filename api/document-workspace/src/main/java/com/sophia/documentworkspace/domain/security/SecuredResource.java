package com.sophia.documentworkspace.domain.security;


import java.util.UUID;

/**
 * Contract every access-controlled resource (artifact, folder, document)
 * fulfils so the policy engine can evaluate it without knowing the concrete
 * type. Implemented by the Artifact hierarchy.
 */
public interface SecuredResource {

    UUID getId();

    UUID getWorkspaceId();

    UUID getOwnerId();
}
