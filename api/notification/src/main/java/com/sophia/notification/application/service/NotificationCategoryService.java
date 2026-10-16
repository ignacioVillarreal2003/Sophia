package com.sophia.notification.application.service;

import com.sophia.notification.application.mapper.NotificationCategoryMapper;
import com.sophia.notification.application.security.AuthenticatedUser;
import com.sophia.notification.domain.dto.request.CreateNotificationCategoryRequest;
import com.sophia.notification.domain.dto.request.UpdateNotificationCategoryRequest;
import com.sophia.notification.domain.dto.response.NotificationCategoryResponse;
import com.sophia.notification.domain.dto.response.PageResponse;
import com.sophia.notification.domain.entity.AuditLog;
import com.sophia.notification.domain.entity.NotificationCategory;
import com.sophia.notification.domain.entity.NotificationCategoryView;
import com.sophia.notification.domain.exception.BadRequestException;
import com.sophia.notification.domain.exception.ConflictException;
import com.sophia.notification.domain.exception.ErrorCode;
import com.sophia.notification.domain.exception.ResourceNotFoundException;
import com.sophia.notification.infrastructure.config.CacheNames;
import com.sophia.notification.infrastructure.repository.NotificationCategoryRepository;
import com.sophia.notification.infrastructure.repository.specification.NotificationCategorySpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationCategoryService {

    private static final String RESOURCE_TYPE = "NotificationCategory";

    private final NotificationCategoryRepository categoryRepository;
    private final NotificationCategoryMapper categoryMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @Cacheable(CacheNames.NOTIFICATION_CATEGORIES)
    public List<NotificationCategoryView> findAll() {
        return categoryRepository.findAll().stream()
                .map(NotificationCategoryView::from)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.NOTIFICATION_CATEGORIES, key = "#code")
    public NotificationCategoryView findByCode(String code) {
        return categoryRepository.findById(code)
                .map(NotificationCategoryView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Notification category", code));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationCategoryResponse> list(String search, Pageable pageable) {
        return PageResponse.from(
                categoryRepository.findAll(
                        NotificationCategorySpecifications.search(search), withDefaultSort(pageable)),
                categoryMapper::toResponse);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.NOTIFICATION_CATEGORIES, allEntries = true)
    public NotificationCategoryResponse create(CreateNotificationCategoryRequest request, AuthenticatedUser actor) {
        if (categoryRepository.existsById(request.code())) {
            throw new ConflictException(ErrorCode.DUPLICATE_RESOURCE,
                    "A notification category with code '%s' already exists".formatted(request.code()));
        }
        NotificationCategory category = categoryRepository.save(NotificationCategory.create(
                request.code(), request.name(), request.description(),
                Boolean.TRUE.equals(request.mandatory()), actor.id()));
        auditService.record(actor.id(), actor.email(), AuditService.CREATE_CATEGORY, RESOURCE_TYPE,
                category.getCode(), AuditLog.Result.SUCCESS,
                AuditService.details("name", request.name(), "description", request.description(),
                        "mandatory", request.mandatory()));
        return categoryMapper.toResponse(category);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.NOTIFICATION_CATEGORIES, allEntries = true)
    public NotificationCategoryResponse update(String code, UpdateNotificationCategoryRequest request,
                                               AuthenticatedUser actor) {
        NotificationCategory category = load(code);
        Snapshot before = Snapshot.of(category);
        if (request.mandatory() != null && request.mandatory() != category.isMandatory() && category.isSystem()) {
            throw new BadRequestException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "The mandatory flag of the built-in category '%s' cannot be changed".formatted(code));
        }
        category.update(request.name(), request.description(), request.mandatory(), actor.id());
        NotificationCategory saved = categoryRepository.save(category);
        auditService.record(actor.id(), actor.email(), AuditService.UPDATE_CATEGORY, RESOURCE_TYPE, code,
                AuditLog.Result.SUCCESS, AuditService.details("before", before));
        return categoryMapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.NOTIFICATION_CATEGORIES, allEntries = true)
    public void delete(String code, AuthenticatedUser actor) {
        NotificationCategory category = load(code);
        if (category.isSystem()) {
            throw new BadRequestException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "The built-in category '%s' cannot be deleted".formatted(code));
        }
        Snapshot before = Snapshot.of(category);
        categoryRepository.delete(category);
        auditService.record(actor.id(), actor.email(), AuditService.DELETE_CATEGORY, RESOURCE_TYPE, code,
                AuditLog.Result.SUCCESS, AuditService.details("before", before));
    }

    private NotificationCategory load(String code) {
        return categoryRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Notification category", code));
    }

    private Pageable withDefaultSort(Pageable pageable) {
        return pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("code"));
    }

    /** State captured before a mutation, recorded in the audit trail. */
    private record Snapshot(String name, String description, boolean mandatory) {

        static Snapshot of(NotificationCategory category) {
            return new Snapshot(category.getName(),
                    category.getDescription() == null ? "" : category.getDescription(),
                    category.isMandatory());
        }
    }
}
