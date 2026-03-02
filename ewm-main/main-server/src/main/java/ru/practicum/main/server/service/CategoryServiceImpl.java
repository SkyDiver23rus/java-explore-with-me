package ru.practicum.main.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.dto.CategoryDto;
import ru.practicum.main.dto.NewCategoryDto;
import ru.practicum.main.server.exception.ConflictException;
import ru.practicum.main.server.exception.NotFoundException;
import ru.practicum.main.server.mapper.CategoryMapper;
import ru.practicum.main.server.model.Category;
import ru.practicum.main.server.repository.CategoryRepository;


import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;

    @Override
    public List<CategoryDto> getCategories(int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);
        return categoryRepository.findAll(pageable)
                .stream()
                .map(CategoryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDto getCategoryById(Long catId) {
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));
        return CategoryMapper.toDto(category);
    }

    @Override
    @Transactional
    public CategoryDto createCategory(NewCategoryDto dto) {
        if (categoryRepository.existsByName(dto.getName())) {
            throw new ConflictException("Категория с именем '" + dto.getName() + "' уже существует");
        }
        Category category = CategoryMapper.toEntity(dto);
        category = categoryRepository.save(category);
        log.info("Создана категория: id={}, name={}", category.getId(), category.getName());
        return CategoryMapper.toDto(category);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto dto) {
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));

        if (!category.getName().equals(dto.getName()) &&
                categoryRepository.existsByName(dto.getName())) {
            throw new ConflictException("Категория с именем '" + dto.getName() + "' уже существует");
        }

        category.setName(dto.getName());
        category = categoryRepository.save(category);
        log.info("Обновлена категория: id={}, name={}", category.getId(), category.getName());
        return CategoryMapper.toDto(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long catId) {
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));

        if (!category.getEvents().isEmpty()) {
            throw new ConflictException("Нельзя удалить категорию, с которой связаны события");
        }

        categoryRepository.delete(category);
        log.info("Удалена категория с id={}", catId);
    }
}