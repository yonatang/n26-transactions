package com.n26.yonatan.model;

import lombok.Data;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * Transaction DB Entity.
 */
@Data
//do not print parent, to eliminate stackoverflow exception in case of circular transactions
@ToString(exclude = "parent")
@Entity
@Table(indexes = {@Index(columnList = "type", name = "type_index")})
public class TransactionEntity {
    @Id
    private Long id;

    @Version
    private Long version;

    @NotNull
    @NotEmpty
    @Pattern(regexp = "[a-zA-Z0-9_]*", message = "can only contain letters, numbers or underscore only")
    private String type;

    // although JSR303 says @Min might not work with double, hibernate validators implementation supports that
    @Min(0)
    private double amount;

    @ManyToOne(fetch = FetchType.LAZY)
    private TransactionEntity parent;

    /**
     * hashCode semantics - if ID exists, use the ID hashCode, otherwise use object reference
     *
     * @return
     */
    public int hashCode() {
        if (id != null) {
            return id.hashCode();
        } else {
            return System.identityHashCode(this);
        }
    }

    /**
     * equals semantics - if ID exists in both objects, assume equality if both IDs equals.
     * Otherwise, compare objects references
     *
     * @param o
     * @return
     */
    public boolean equals(Object o) {
        if (o == null || !(o instanceof TransactionEntity)) {
            return false;
        }
        TransactionEntity t = (TransactionEntity) o;
        if (t.getId() == null && getId() == null) {
            return t == o;
        }
        if (getId() == null || t.getId() == null) {
            return false;
        }
        return getId().equals(t.getId());
    }
}
