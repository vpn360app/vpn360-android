# VPN 360 para Android

App de VPN de **VPN 360**. Está basada en [v2rayNG](https://github.com/2dust/v2rayNG),
software libre con licencia GPL-3.0, y por eso este código también es público y conserva la misma licencia (ver `LICENSE`).

## Descargar

Última versión: **[Descargar VPN 360](https://github.com/vpn360app/vpn360-android/releases/latest/download/VPN360-universal.apk)**
(funciona en cualquier Android 7 o superior).

## Cambios respecto a v2rayNG

- Nombre, ícono y colores de VPN 360; identificador de la app `com.vpn360.app`.
- Enlace para importar la suscripción con un toque: `vpn360://install-sub?url=<enlace-de-suscripción>`.
- Las actualizaciones y los enlaces de la app apuntan a este repositorio.
- Se quitó el enlace promocional del menú.

## Cómo se construye

Cada cambio en `main` se compila automáticamente con GitHub Actions (`.github/workflows/build.yml`),
se firma con la llave de VPN 360 y se publica en *Releases*. La llave va cifrada en `signing/` y solo se puede usar
con el secreto `KEYSTORE_PASSWORD` del repositorio.
