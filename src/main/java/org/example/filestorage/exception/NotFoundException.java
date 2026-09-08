package org.example.filestorage.exception;

// Один общий класс на все сущности вместо FileNotFoundException/UserNotFoundException/...
// — по образцу реальной практики (проверено на коде BNPL: EntityNotFoundException + ключ,
// не толпа мелких классов на каждую сущность).
public class NotFoundException extends RuntimeException {

    public NotFoundException(String entityName, Object id) {
        super(entityName + " не найден: id=" + id);
    }
}
