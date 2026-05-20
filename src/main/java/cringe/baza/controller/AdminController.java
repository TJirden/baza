package cringe.baza.controller;

import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import cringe.baza.repository.jpa.MemeGroupRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

  private final TelegramUserRepository userRepository;
  private final MemeGroupRepository groupRepository;
  private final MemeProcessor memeProcessor;

  @GetMapping("/memes")
  public List<Meme> listMemes(
      @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) {
    return memeProcessor.getAll(limit, offset);
  }

  @DeleteMapping("/memes/{id}")
  public ResponseEntity<Void> deleteMeme(@PathVariable String id) {
    if (memeProcessor.delete(id)) {
      return ResponseEntity.ok().build();
    }
    return ResponseEntity.notFound().build();
  }

  @PatchMapping("/memes/{id}")
  public ResponseEntity<Void> updateMeme(
      @PathVariable String id, @RequestBody String newDescription) {
    if (memeProcessor.update(id, newDescription)) {
      return ResponseEntity.ok().build();
    }
    return ResponseEntity.notFound().build();
  }

  @GetMapping("/users")
  public List<TelegramUser> listUsers() {
    return userRepository.findAll();
  }

  @GetMapping("/groups")
  public List<MemeGroup> listGroups() {
    return groupRepository.findAll();
  }

  @DeleteMapping("/users/{id}")
  public void deleteUser(@PathVariable Long id) {
    userRepository.deleteById(id);
  }

  @DeleteMapping("/groups/{id}")
  public void deleteGroup(@PathVariable Long id) {
    groupRepository.deleteById(id);
  }

  @GetMapping("/memes/search")
  public List<Meme> searchMemes(@RequestParam String q) {
    return memeProcessor.searchWithIds(q, 50, null, null);
  }

  @GetMapping("/users/search")
  public List<TelegramUser> searchUsers(@RequestParam String q) {
    return userRepository.findByUsernameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(q, q);
  }

  @GetMapping("/groups/search")
  public List<MemeGroup> searchGroups(@RequestParam String q) {
    return groupRepository.findByNameContainingIgnoreCase(q);
  }
}
