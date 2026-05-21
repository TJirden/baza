package cringe.baza.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meme_moderation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemeModeration {

    @Id
    private String id;

    private String fileId;

    @Column(length = 2000)
    private String description;

    private Long ownerId;
    private String visibility;
    private String groupIds;

    private String status;

    @Column(length = 1000)
    private String moderationReason;
}
