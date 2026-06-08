package com.backend.controller;

import com.backend.dto.pack.PackResponseDTO;
import com.backend.service.PackService;
import com.backend.service.TextToSpeechService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/packs")
@RequiredArgsConstructor
public class PublicPackController {

    private final PackService packService;
    private final TextToSpeechService textToSpeechService;

    @GetMapping("/active")
    public ResponseEntity<List<PackResponseDTO>> getActivePacks() {
        return ResponseEntity.ok(packService.getActivePacks());
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<PackResponseDTO>> getByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(packService.getActivePacksByCategory(categoryId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PackResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(packService.getActivePackById(id));
    }

    @PostMapping("/{id}/text-to-speech")
    public ResponseEntity<byte[]> getPackTextToSpeech(@PathVariable Long id) {
        PackResponseDTO pack = packService.getActivePackById(id);
        TextToSpeechService.AudioResult audioResult = textToSpeechService.synthesizePack(pack);

        MediaType mediaType = MediaType.parseMediaType(audioResult.contentType());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"pack-" + id + ".mp3\"")
                .body(audioResult.audioBytes());
    }
}

