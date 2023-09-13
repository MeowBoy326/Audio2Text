package com.example.audio2text

import java.util.concurrent.TimeoutException

class DownloadTimeoutException(val url: String) : TimeoutException("Le téléchargement a expiré !")
