package com.company.logicstic.modules.fleet.service.impl;

import com.company.logicstic.modules.fleet.entity.Container;
import com.company.logicstic.modules.fleet.repository.ContainerRepository;
import com.company.logicstic.modules.fleet.service.ContainerService;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContainerServiceImpl implements ContainerService {

  private final ContainerRepository containerRepository;

  @Override
  public Container getEntityById(UUID id) {
    return containerRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Container not found: " + id));
  }
}
