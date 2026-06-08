package tn.esprit.inscriptionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.inscriptionservice.entity.Inscription;
import tn.esprit.inscriptionservice.entity.InscriptionStatut;

import java.util.List;

@Repository
public interface InscriptionRepository extends JpaRepository<Inscription, Long> {

    List<Inscription> findByStudentId(long studentId);

    boolean existsByStudentIdAndEventId(long studentId, long eventId);

    boolean existsByStudentIdAndEventIdAndStatut(long studentId, long eventId, InscriptionStatut statut);

    Inscription findByStudentIdAndEventId(long studentId, long eventId);

    long countByEventIdAndStatut(long eventId, InscriptionStatut statut);

    List<Inscription> findByStatutOrderByDateInscriptionDesc(InscriptionStatut statut);

    List<Inscription> findByStatutInOrderByDateInscriptionDesc(List<InscriptionStatut> statuts);

    Inscription findFirstByEventIdAndStatutOrderByDateInscriptionAsc(long eventId, InscriptionStatut statut);

    List<Inscription> findByEventIdAndStatutOrderByDateInscriptionAsc(long eventId, InscriptionStatut statut);

    @Query("select distinct i.eventId from Inscription i where i.studentId = :studentId order by i.eventId asc")
    List<Long> findDistinctEventIdsByStudentId(@Param("studentId") long studentId);

    List<Inscription> findByStudentIdOrderByDateInscriptionDesc(long studentId);
}

//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//import tn.esprit.inscriptionservice.entity.Inscription;
//import tn.esprit.inscriptionservice.entity.InscriptionStatut;
//
//import java.util.List;
//
//@Repository
//public interface InscriptionRepository extends JpaRepository<Inscription, Long> {
//
//    List<Inscription> findByStudentId(long studentId);
//
//    boolean existsByStudentIdAndEventId(long studentId, long eventId);
//
//    long countByEventIdAndStatut(long eventId, InscriptionStatut statut);
//
//    List<Inscription> findByStatutOrderByDateInscriptionDesc(InscriptionStatut statut);
//
//    @Query("select distinct i.eventId from Inscription i where i.studentId = :studentId order by i.eventId asc")
//    List<Long> findDistinctEventIdsByStudentId(@Param("studentId") long studentId);
//
//    List<Inscription> findByStudentIdOrderByDateInscriptionDesc(long studentId);
//}
