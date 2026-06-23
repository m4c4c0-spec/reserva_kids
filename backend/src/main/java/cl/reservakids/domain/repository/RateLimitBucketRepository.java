package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.RateLimitBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RateLimitBucketRepository extends JpaRepository<RateLimitBucket, Long>, RateLimitBucketRepositoryCustom {

    Optional<RateLimitBucket> findByIpAndRutaTipo(String ip, String rutaTipo);

    @Modifying
    @Query("DELETE FROM RateLimitBucket b WHERE b.epochMinuto < :minutoCorte")
    int purgarExpirados(@Param("minutoCorte") long minutoCorte);
}
