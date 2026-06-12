from flask import render_template, request, redirect, url_for
from app.models.service import Service

def services():
    if request.method == 'POST':
        name = request.form['name']
        description = request.form['description']
        new_service = Service(name=name, description=description)
        new_service.save()
        return redirect(url_for('services'))
    services = Service.get_all()
    return render_template('services.html', services=services)
