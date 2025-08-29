package com.mobifone.vdi.entity;

import com.mobifone.vdi.utils.H;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.util.Date;

@Setter
@Getter
@ToString
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties(value = { "createdBy", "createdDate", "updatedBy", "updatedDate", "isDeleted", "version" }, allowGetters = true)
public abstract class AbstractAuditingEntity<T> implements Serializable {

    public abstract T getId();

    @CreatedBy
    @Column(name = "created_by")
    private String createdBy;

    @CreatedDate
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_date")
    protected Date createdDate;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @LastModifiedDate
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_date")
    protected Date updatedDate;

    @Column(name = "is_deleted")
    protected Long isDeleted;

    @Column(name = "version")
    protected Long version;


    @PrePersist
    public void prePersist() {
        this.version = 1L;
        this.isDeleted = 0L;
    }

    @PreUpdate
    public void preUpdate() {
        this.version = H.nvl(this.getVersion(), 0L) + 1;
    }
}