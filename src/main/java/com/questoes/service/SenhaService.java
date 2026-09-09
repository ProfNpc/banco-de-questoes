package com.questoes.service;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class SenhaService {

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Gera uma senha aleatória de n caracteres */
    public String gerarSenhaAleatoria(int tamanho) {
        StringBuilder sb = new StringBuilder(tamanho);
        for (int i = 0; i < tamanho; i++) sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        return sb.toString();
    }

    /** Gera um salt aleatório de 32 bytes (64 hex) */
    public String gerarSalto() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Calcula SHA-256(salto + senha) e retorna hex */
    public String calcularHash(String salto, String senha) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((salto + senha).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao calcular hash de senha", e);
        }
    }

    /** Verifica se a senha está correta comparando com o hash armazenado */
    public boolean verificar(String salto, String hashArmazenado, String senhaFornecida) {
        return calcularHash(salto, senhaFornecida).equals(hashArmazenado);
    }
}
