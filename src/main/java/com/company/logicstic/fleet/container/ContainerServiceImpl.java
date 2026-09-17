package com.company.logicstic.fleet.container;

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
