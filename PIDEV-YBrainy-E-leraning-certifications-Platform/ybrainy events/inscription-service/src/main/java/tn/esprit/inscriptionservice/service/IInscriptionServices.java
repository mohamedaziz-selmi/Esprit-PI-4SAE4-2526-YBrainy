package tn.esprit.inscriptionservice.service;

import tn.esprit.inscriptionservice.entity.Inscription;
import tn.esprit.inscriptionservice.entity.InscriptionStatut;

import java.util.List;

public interface IInscriptionServices {
    Inscription createInscription(long eventId, long studentId, InscriptionStatut initialStatus);
    void updateStatus(long idInscription, InscriptionStatut status);
    void cancelByStudentAndEvent(long studentId, long eventId);
    List<Inscription> getPendingInscriptions();
    List<Inscription> getWaitlistByEvent(long eventId);
    List<tn.esprit.inscriptionservice.entity.AdminNotification> getAdminNotifications();
    List<Long> getDistinctEventIdsByStudent(long studentId);
    List<Inscription> getEventStatusesByStudent(long studentId);
    boolean existsByStudentAndEvent(long studentId, long eventId);
    boolean hasConfirmedInscription(long studentId, long eventId);
    long countConfirmedByEvent(long eventId);
}

//import tn.esprit.inscriptionservice.entity.Inscription;
//import tn.esprit.inscriptionservice.entity.InscriptionStatut;
//
//import java.util.List;
//
//public interface IInscriptionServices {
//    Inscription createInscription(long eventId, long studentId);
//    void updateStatus(long idInscription, InscriptionStatut status);
//    List<Inscription> getPendingInscriptions();
//    List<Long> getDistinctEventIdsByStudent(long studentId);
//    List<Inscription> getEventStatusesByStudent(long studentId);
//    boolean existsByStudentAndEvent(long studentId, long eventId);
//    long countConfirmedByEvent(long eventId);
//}
