package org.example.filestorage.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("files")
public class File {

    @Id
    @Column("id")
    private Integer id;

    @Column("name")
    private String name;

    @Column("location")
    private String location;

    @Column("status")
    private FileStatus status;
}
