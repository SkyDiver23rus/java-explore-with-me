package ru.practicum.main.server.service;

import ru.practicum.main.server.dto.CompilationDto;
import ru.practicum.main.server.dto.NewCompilationDto;
import ru.practicum.main.server.dto.UpdateCompilationRequest;

import java.util.List;

public interface CompilationService {
    List<CompilationDto> getCompilations(Boolean pinned, int from, int size);

    CompilationDto getCompilationById(Long compId);

    CompilationDto createCompilation(NewCompilationDto dto);

    CompilationDto updateCompilation(Long compId, UpdateCompilationRequest dto);

    void deleteCompilation(Long compId);
}