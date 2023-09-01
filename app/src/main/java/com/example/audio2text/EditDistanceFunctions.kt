package com.example.audio2text

fun LevenshteinDistance(str1 : String , str2 : String) : Int {

    fun array2dOfInt(sizeOuter: Int, sizeInner: Int): Array<IntArray>
            = Array(sizeOuter) { IntArray(sizeInner) }

    fun min( a : Int , b : Int , c : Int ) : Int  = Math.min( a , Math.min(b,c) )

    val arr = array2dOfInt(str1.length + 1 , str2.length + 1)

    for (col in 0..str2.length)
        arr[0][col] = col

    for( row in 1..str1.length )
        arr[row][0] = row

    for( row in 1..str1.length ){
        for( col in 1..str2.length ){

            if( str1[row-1] == str2[col-1] ) {
                arr[row][col] = arr[row - 1][col - 1]
            }
            else{
                arr[row][col] = 1 + min( arr[row][col-1] , arr[row-1][col] , arr[row-1][col-1] )
            }
        }
    }

    return arr[str1.length][str2.length]
}

fun DamerauLevenshtein(str1: String, str2: String): Int {
    val m = str1.length
    val n = str2.length

    // Initialisation du tableau
    val d = Array(m + 1) { IntArray(n + 1) }

    for (i in 0..m) {
        d[i][0] = i
    }

    for (j in 0..n) {
        d[0][j] = j
    }

    // Boucle de récurrence
    for (i in 1..m) {
        for (j in 1..n) {
            val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1

            d[i][j] = minOf(
                d[i - 1][j] + 1,
                d[i][j - 1] + 1,
                d[i - 1][j - 1] + cost
            )

            // Gestion de la transposition
            if (i > 1 && j > 1 && str1[i - 1] == str2[j - 2] && str1[i - 2] == str2[j - 1]) {
                d[i][j] = minOf(
                    d[i][j],
                    d[i - 2][j - 2] + cost
                )
            }
        }
    }

    return d[m][n]
}